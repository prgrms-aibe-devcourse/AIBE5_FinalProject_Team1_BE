package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team1.codedock.domain.github.dto.GithubWebhookRegisterResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookRegistrationService {

    private static final String GITHUB_API = "https://api.github.com";
    private static final String AUTHORITY_OWNER = "owner";
    private static final String AUTHORITY_ADMIN = "admin";

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final RestClient.Builder restClientBuilder;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    @Transactional
    public GithubWebhookRegisterResponse registerWebhook(Long workspaceId, Long repositoryId, Long userId) {
        validateAuthority(workspaceId, userId);

        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        if (!repo.getWorkspace().getId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        String webhookSecret = UUID.randomUUID().toString().replace("-", "");
        // 경로는 DB id 대신 GitHub repo id를 사용해 DB 재생성/재연결 뒤에도 URL을 안정적으로 유지함.
        String webhookUrl = appBaseUrl + "/api/v1/github/webhooks/gh/" + repo.getGithubRepoId();

        RestClient client = restClientBuilder.clone()
                .baseUrl(GITHUB_API)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        // 멱등 처리: 같은 레포를 가리키는 기존 CodeDock webhook을 정리한 뒤,
        // 아직 추적 가능한 hook이 남아 있으면 PATCH하고 없으면 새로 생성함.
        deleteOurHooks(client, repo);
        GithubHookResponse existingHook = findExistingHook(fetchHooks(client, repo), repo);

        GithubHookResponse hookResponse;
        try {
            if (existingHook != null) {
                hookResponse = client.method(HttpMethod.PATCH)
                        .uri("/repos/{owner}/{repo}/hooks/{hookId}", repo.getOwner(), repo.getName(), existingHook.id())
                        .body(webhookRequestBody(webhookUrl, webhookSecret))
                        .retrieve()
                        .body(GithubHookResponse.class);
            } else {
                hookResponse = client.post()
                        .uri("/repos/{owner}/{repo}/hooks", repo.getOwner(), repo.getName())
                        .body(webhookRequestBody(webhookUrl, webhookSecret))
                        .retrieve()
                        .body(GithubHookResponse.class);
            }
        } catch (Exception e) {
            log.error("GitHub Webhook 등록 실패 - repo={}/{}", repo.getOwner(), repo.getName(), e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        if (hookResponse == null) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        repo.updateWebhook(String.valueOf(hookResponse.id()), webhookSecret, webhookUrl, true);

        return new GithubWebhookRegisterResponse(repositoryId, String.valueOf(hookResponse.id()), webhookUrl, true);
    }

    // 같은 GitHub repo를 가리키는 CodeDock webhook을 모두 찾아 삭제함.
    // 신규 URL(/gh/{githubRepoId})뿐 아니라 과거 DB id 기반 URL(/webhooks/{id})도 정리 대상임.
    // 목록 조회가 실패하면 최소한 현재 DB가 추적 중인 hook만이라도 삭제를 시도함.
    private void deleteOurHooks(RestClient client, GithubRepository repo) {
        Set<String> ourPaths = new HashSet<>();
        ourPaths.add("/api/v1/github/webhooks/gh/" + repo.getGithubRepoId());
        for (GithubRepository row : githubRepositoryRepository.findAllByGithubRepoId(repo.getGithubRepoId())) {
            ourPaths.add("/api/v1/github/webhooks/" + row.getId());
        }

        try {
            GithubHookListItem[] hooks = client.get()
                    .uri("/repos/{owner}/{repo}/hooks", repo.getOwner(), repo.getName())
                    .retrieve()
                    .body(GithubHookListItem[].class);
            if (hooks == null) {
                return;
            }

            for (GithubHookListItem hook : hooks) {
                String url = hook.config() != null ? hook.config().url() : null;
                if (url != null && ourPaths.stream().anyMatch(url::endsWith)) {
                    deleteHookById(client, repo, String.valueOf(hook.id()));
                }
            }
        } catch (Exception e) {
            log.warn("기존 Webhook 목록 조회/정리 실패(무시) - repo={}/{}", repo.getOwner(), repo.getName(), e);
            String tracked = repo.getWebhookId();
            if (tracked != null && !tracked.isBlank()) {
                deleteHookById(client, repo, tracked);
            }
        }
    }

    private void deleteHookById(RestClient client, GithubRepository repo, String hookId) {
        try {
            client.delete()
                    .uri("/repos/{owner}/{repo}/hooks/{hookId}", repo.getOwner(), repo.getName(), hookId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 이미 삭제됐거나 권한 문제로 실패해도 신규 등록 흐름은 계속 진행함.
            log.warn("Webhook 삭제 실패(무시) - repo={}/{}, hookId={}", repo.getOwner(), repo.getName(), hookId, e);
        }
    }

    private void validateAuthority(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        var member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        String auth = member.getAuthority() == null ? "" : member.getAuthority().toLowerCase();
        if (!AUTHORITY_OWNER.equals(auth) && !AUTHORITY_ADMIN.equals(auth)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private List<GithubHookResponse> fetchHooks(RestClient client, GithubRepository repo) {
        try {
            GithubHookResponse[] hooks = client.get()
                    .uri("/repos/{owner}/{repo}/hooks", repo.getOwner(), repo.getName())
                    .retrieve()
                    .body(GithubHookResponse[].class);
            return hooks == null ? List.of() : Arrays.asList(hooks);
        } catch (Exception e) {
            log.warn("GitHub Webhook 목록 조회 실패. 새 hook 등록으로 진행함 - repo={}/{}",
                    repo.getOwner(), repo.getName(), e);
            return List.of();
        }
    }

    private GithubHookResponse findExistingHook(List<GithubHookResponse> hooks, GithubRepository repo) {
        if (hooks.isEmpty()) {
            return null;
        }

        String storedHookId = repo.getWebhookId();
        if (hasText(storedHookId)) {
            for (GithubHookResponse hook : hooks) {
                if (storedHookId.equals(String.valueOf(hook.id()))) {
                    return hook;
                }
            }
        }

        String storedWebhookUrl = repo.getWebhookUrl();
        if (hasText(storedWebhookUrl)) {
            for (GithubHookResponse hook : hooks) {
                if (storedWebhookUrl.equals(hook.configUrl())) {
                    return hook;
                }
            }
        }

        return null;
    }

    private Map<String, Object> webhookRequestBody(String webhookUrl, String webhookSecret) {
        return Map.of(
                "name", "web",
                "active", true,
                "events", List.of("issues", "push", "pull_request", "pull_request_review"),
                "config", Map.of(
                        "url", webhookUrl,
                        "content_type", "json",
                        "secret", webhookSecret,
                        "insecure_ssl", "0"
                )
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubHookResponse(
            @JsonProperty("id") long id,
            @JsonProperty("config") GithubHookConfig config
    ) {
        private String configUrl() {
            return config == null ? null : config.url();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubHookListItem(@JsonProperty("id") long id, @JsonProperty("config") GithubHookConfig config) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubHookConfig(@JsonProperty("url") String url) {}
}
