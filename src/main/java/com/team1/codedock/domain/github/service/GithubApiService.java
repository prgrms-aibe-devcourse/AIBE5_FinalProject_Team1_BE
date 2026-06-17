package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.team1.codedock.domain.github.dto.GithubCollaboratorResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubApiService {

    private final UserRepository userRepository;

    // Spring Boot 자동 설정된 RestClient.Builder 주입 (Jackson ObjectMapper 포함)
    private final Builder restClientBuilder;

    private static final String GITHUB_API = "https://api.github.com";

    private RestClient githubClient(String token) {
        return restClientBuilder
                .clone()
                .baseUrl(GITHUB_API)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }

    public List<GithubRepoResponse> getUserRepos(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("GitHub 토큰 없음 → userId={}", currentUserId);
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        RestClient client = githubClient(token);

        List<GithubRepoApiItem> items;
        try {
            items = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/user/repos")
                            .queryParam("per_page", "100")
                            .queryParam("sort", "updated")
                            .queryParam("affiliation", "owner,collaborator")
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GithubRepoApiItem>>() {});
        } catch (HttpClientErrorException e) {
            log.warn("GitHub API 4xx 오류 → userId={}, status={}, body={}",
                    currentUserId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        } catch (Exception e) {
            log.error("GitHub API 호출 실패 → userId={}", currentUserId, e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        if (items == null) return List.of();

        String myLogin = user.getGithubUsername();
        return items.stream()
                .map(item -> GithubRepoResponse.builder()
                        .id(item.id())
                        .name(item.name())
                        .fullName(item.fullName())
                        .owner(item.owner() != null ? item.owner().login() : "")
                        .isPrivate(item.isPrivate())
                        .language(item.language())
                        .htmlUrl(item.htmlUrl())
                        .defaultBranch(item.defaultBranch())
                        .relation(myLogin != null && myLogin.equalsIgnoreCase(
                                item.owner() != null ? item.owner().login() : "") ? "owner" : "collaborator")
                        .build())
                .toList();
    }

    public GithubRepoResponse getRepo(String owner, String repo, String token) {
        RestClient client = githubClient(token);

        GithubRepoApiItem item;
        try {
            item = client.get()
                    .uri("/repos/{owner}/{repo}", owner, repo)
                    .retrieve()
                    .body(GithubRepoApiItem.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
        } catch (HttpClientErrorException e) {
            log.warn("GitHub 레포 조회 4xx → {}/{}, status={}", owner, repo, e.getStatusCode());
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        } catch (Exception e) {
            log.error("GitHub 레포 조회 실패 → {}/{}", owner, repo, e);
            throw new BusinessException(ErrorCode.GITHUB_API_ERROR);
        }

        if (item == null) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
        }

        return GithubRepoResponse.builder()
                .id(item.id())
                .name(item.name())
                .fullName(item.fullName())
                .owner(item.owner() != null ? item.owner().login() : "")
                .isPrivate(item.isPrivate())
                .language(item.language())
                .htmlUrl(item.htmlUrl())
                .defaultBranch(item.defaultBranch())
                .build();
    }

    public List<GithubCollaboratorResponse> getRepoCollaborators(Long currentUserId, String owner, String repo) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        RestClient client = githubClient(token);

        List<GithubCollaboratorApiItem> items;
        try {
            items = client.get()
                    .uri("/repos/{owner}/{repo}/collaborators", owner, repo)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<GithubCollaboratorApiItem>>() {});
        } catch (HttpClientErrorException e) {
            log.warn("GitHub 협업자 API 4xx → {}/{}, status={}", owner, repo, e.getStatusCode());
            return List.of();
        } catch (Exception e) {
            log.warn("GitHub 협업자 API 실패 → {}/{}", owner, repo, e);
            return List.of();
        }

        if (items == null) return List.of();

        String myLogin = user.getGithubUsername();
        List<String> logins = items.stream()
                .map(GithubCollaboratorApiItem::login)
                .filter(login -> !login.equalsIgnoreCase(myLogin))
                .toList();

        Map<String, User> registeredUsers = userRepository.findByGithubUsernameIn(logins).stream()
                .collect(Collectors.toMap(
                        u -> u.getGithubUsername().toLowerCase(),
                        Function.identity()
                ));

        List<GithubCollaboratorResponse> result = new ArrayList<>();
        for (GithubCollaboratorApiItem item : items) {
            if (item.login().equalsIgnoreCase(myLogin)) continue;
            User platformUser = registeredUsers.get(item.login().toLowerCase());
            result.add(GithubCollaboratorResponse.builder()
                    .login(item.login())
                    .avatarUrl(item.avatarUrl())
                    .htmlUrl(item.htmlUrl())
                    .userId(platformUser != null ? platformUser.getId() : null)
                    .email(platformUser != null ? platformUser.getEmail() : null)
                    .displayName(platformUser != null
                            ? (platformUser.getDisplayName() != null
                                    ? platformUser.getDisplayName()
                                    : platformUser.getUsername())
                            : null)
                    .build());
        }
        return result;
    }

    // ── GitHub API 응답 역직렬화용 내부 DTO ──────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record GithubRepoApiItem(
            @JsonProperty("id")              long id,
            @JsonProperty("name")            String name,
            @JsonProperty("full_name")       String fullName,
            @JsonProperty("owner")           OwnerItem owner,
            @JsonProperty("private")         boolean isPrivate,
            @JsonProperty("language")        String language,
            @JsonProperty("html_url")        String htmlUrl,
            @JsonProperty("default_branch")  String defaultBranch
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        static record OwnerItem(@JsonProperty("login") String login) {}
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static record GithubCollaboratorApiItem(
            @JsonProperty("login")       String login,
            @JsonProperty("avatar_url")  String avatarUrl,
            @JsonProperty("html_url")    String htmlUrl
    ) {}
}
