package com.team1.codedock.domain.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.entity.ApiSpec;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.github.service.GithubApiClient;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiSpecAiService {

    private final ApiSpecRepository apiSpecRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubApiClient githubApiClient;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Transactional
    public List<ApiSpecResponse> generateChecklist(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (workspace.getSwaggerUrl() == null || workspace.getSwaggerUrl().isBlank()) {
            throw new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED);
        }

        Long userId = SecurityUtils.getCurrentUserId();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        List<GithubRepository> githubRepos = githubRepositoryRepository.findByWorkspaceId(workspaceId)
                .stream().limit(3).toList();
        if (githubRepos.isEmpty()) throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);

        List<String> repoSources = githubRepos.stream()
                .flatMap(repo -> githubApiClient.fetchRepoSources(
                        repo.getOwner(), repo.getName(), repo.getDefaultBranch(),
                        user.getGithubAccessToken()).stream())
                .toList();

        String swaggerJson = fetchSwaggerJson(workspace.getSwaggerUrl());
        String compressedSwagger = compressSwaggerJson(swaggerJson);
        GeminiClient.ApiSpecChecklistResult result = geminiClient.generateApiSpecChecklist(compressedSwagger, repoSources);

        if (result == null || result.checklist() == null || result.checklist().isEmpty()) {
            return List.of();
        }

        Set<String> validMethods = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");

        List<ApiSpec> specs = result.checklist().stream()
                .filter(item -> item.title() != null && item.method() != null && item.endpoint() != null)
                .filter(item -> validMethods.contains(item.method().toUpperCase()))
                .map(item -> ApiSpec.createFromAi(
                        workspace, member,
                        item.title(), item.method().toUpperCase(), item.endpoint(),
                        item.groupName(), item.summary(), item.description()
                ))
                .toList();

        return apiSpecRepository.saveAll(specs).stream()
                .map(ApiSpecResponse::from)
                .toList();
    }

    private String compressSwaggerJson(String swaggerJson) {
        try {
            JsonNode root = objectMapper.readTree(swaggerJson);
            JsonNode paths = root.path("paths");
            if (paths.isMissingNode()) return swaggerJson;

            StringBuilder sb = new StringBuilder();
            paths.fields().forEachRemaining(pathEntry -> {
                String endpoint = pathEntry.getKey();
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    JsonNode op = methodEntry.getValue();
                    String title = op.path("operationId").asText(endpoint);
                    String groupName = op.has("tags") && op.get("tags").size() > 0
                            ? op.get("tags").get(0).asText() : "";
                    sb.append(method).append(" ").append(endpoint)
                            .append(" [").append(title).append("]")
                            .append(" (").append(groupName).append(")\n");
                });
            });
            return sb.toString();
        } catch (Exception e) {
            return swaggerJson;
        }
    }

    private String fetchSwaggerJson(String swaggerUrl) {
        try {
            return restClientBuilder.build()
                    .get()
                    .uri(swaggerUrl)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SWAGGER_FETCH_ERROR);
        }
    }
}
