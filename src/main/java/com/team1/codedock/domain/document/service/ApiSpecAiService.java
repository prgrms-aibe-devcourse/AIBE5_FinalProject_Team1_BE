package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.entity.ApiSpec;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
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
    private final GeminiClient geminiClient;
    private final RestClient.Builder restClientBuilder;

    @Transactional
    public List<ApiSpecResponse> generateChecklist(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (workspace.getSwaggerUrl() == null || workspace.getSwaggerUrl().isBlank()) {
            throw new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED);
        }

        Long userId = SecurityUtils.getCurrentUserId();
        WorkspaceMember member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        String swaggerJson = fetchSwaggerJson(workspace.getSwaggerUrl());
        GeminiClient.ApiSpecChecklistResult result = geminiClient.generateApiSpecChecklist(swaggerJson);

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
