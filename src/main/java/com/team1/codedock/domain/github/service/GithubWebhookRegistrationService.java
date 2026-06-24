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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
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
        String webhookUrl = appBaseUrl + "/api/v1/github/webhooks/" + repositoryId;

        RestClient client = restClientBuilder.clone()
                .baseUrl(GITHUB_API)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();

        GithubHookResponse hookResponse;
        try {
            hookResponse = client.post()
                    .uri("/repos/{owner}/{repo}/hooks", repo.getOwner(), repo.getName())
                    .body(Map.of(
                            "name", "web",
                            "active", true,
                            "events", List.of("issues", "push", "pull_request", "pull_request_review"),
                            "config", Map.of(
                                    "url", webhookUrl,
                                    "content_type", "json",
                                    "secret", webhookSecret,
                                    "insecure_ssl", "0"
                            )
                    ))
                    .retrieve()
                    .body(GithubHookResponse.class);
        } catch (Exception e) {
            log.error("GitHub Webhook 등록 실패 → repo={}/{}", repo.getOwner(), repo.getName(), e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        if (hookResponse == null) {
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        repo.updateWebhook(String.valueOf(hookResponse.id()), webhookSecret, webhookUrl, true);

        return new GithubWebhookRegisterResponse(repositoryId, String.valueOf(hookResponse.id()), webhookUrl, true);
    }

    private void validateAuthority(Long workspaceId, Long userId) {
        if (userId == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        String auth = member.getAuthority() == null ? "" : member.getAuthority().toLowerCase();
        if (!AUTHORITY_OWNER.equals(auth) && !AUTHORITY_ADMIN.equals(auth)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubHookResponse(@JsonProperty("id") long id) {}
}
