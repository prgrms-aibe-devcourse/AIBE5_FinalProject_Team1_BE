package com.team1.codedock.domain.document.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.document.dto.SwaggerSyncResponse;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiSpecSwaggerService {

    private final ApiSpecRepository apiSpecRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Transactional
    public SwaggerSyncResponse registerAndSync(Long workspaceId, String swaggerUrl) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        WorkspaceMember member = findCurrentMember(workspaceId);

        workspace.updateSwaggerUrl(swaggerUrl);

        return doSync(workspace, member);
    }

    @Transactional
    public SwaggerSyncResponse resync(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (workspace.getSwaggerUrl() == null || workspace.getSwaggerUrl().isBlank()) {
            throw new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED);
        }
        WorkspaceMember member = findCurrentMember(workspaceId);

        return doSync(workspace, member);
    }

    public String getSwaggerUrl(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        if (workspace.getSwaggerUrl() == null || workspace.getSwaggerUrl().isBlank()) {
            throw new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED);
        }
        return workspace.getSwaggerUrl();
    }

    private SwaggerSyncResponse doSync(Workspace workspace, WorkspaceMember member) {
        List<ApiSpec> parsed = fetchAndParse(workspace, member);

        apiSpecRepository.deleteAllByWorkspace_IdAndSourceType(workspace.getId(), "swagger");
        apiSpecRepository.saveAll(parsed);

        Set<String> swaggerKeys = parsed.stream()
                .map(s -> s.getEndpoint() + ":" + s.getMethod())
                .collect(Collectors.toSet());

        long completedCount = apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(workspace.getId(), "swagger")
                .stream()
                .filter(s -> !"completed".equals(s.getStatus()))
                .filter(s -> swaggerKeys.contains(s.getEndpoint() + ":" + s.getMethod()))
                .peek(ApiSpec::complete)
                .count();

        return new SwaggerSyncResponse(workspace.getSwaggerUrl(), parsed.size(), (int) completedCount);
    }

    private List<ApiSpec> fetchAndParse(Workspace workspace, WorkspaceMember member) {
        try {
            String json = restClientBuilder.build()
                    .get()
                    .uri(workspace.getSwaggerUrl())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            JsonNode paths = root.path("paths");

            List<ApiSpec> specs = new ArrayList<>();
            List<String> validMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE");

            paths.fields().forEachRemaining(pathEntry -> {
                String endpoint = pathEntry.getKey();
                pathEntry.getValue().fields().forEachRemaining(methodEntry -> {
                    String method = methodEntry.getKey().toUpperCase();
                    if (!validMethods.contains(method)) return;

                    JsonNode op = methodEntry.getValue();
                    String title = op.path("operationId").asText(endpoint);
                    String summary = nullIfEmpty(op.path("summary").asText(null));
                    String description = nullIfEmpty(op.path("description").asText(null));
                    String groupName = op.has("tags") && op.get("tags").size() > 0
                            ? op.get("tags").get(0).asText() : null;

                    specs.add(ApiSpec.createFromSwagger(
                            workspace, member,
                            title, method, endpoint, groupName, summary, description,
                            extractParameters(op, "path"),
                            extractParameters(op, "header"),
                            extractParameters(op, "query"),
                            extractRequestBody(op),
                            extractFirstResponseBody(op),
                            extractFirstResponseStatus(op)
                    ));
                });
            });
            return specs;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SWAGGER_FETCH_ERROR);
        }
    }

    private WorkspaceMember findCurrentMember(Long workspaceId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
    }

    private String nullIfEmpty(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String extractParameters(JsonNode op, String in) {
        if (!op.has("parameters")) return null;
        List<JsonNode> params = new ArrayList<>();
        op.get("parameters").forEach(p -> {
            if (in.equals(p.path("in").asText())) params.add(p);
        });
        if (params.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            return null;
        }
    }

    private String extractRequestBody(JsonNode op) {
        if (!op.has("requestBody")) return null;
        try {
            return objectMapper.writeValueAsString(op.get("requestBody"));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer extractFirstResponseStatus(JsonNode op) {
        if (!op.has("responses")) return null;
        try {
            return Integer.parseInt(op.get("responses").fieldNames().next());
        } catch (Exception e) {
            return null;
        }
    }

    private String extractFirstResponseBody(JsonNode op) {
        if (!op.has("responses")) return null;
        try {
            return objectMapper.writeValueAsString(op.get("responses").elements().next());
        } catch (Exception e) {
            return null;
        }
    }
}
