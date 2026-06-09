package com.team1.codedock.domain.document.dto;

import com.team1.codedock.domain.document.entity.ApiSpec;

import java.time.LocalDateTime;

public record ApiSpecResponse(
        Long id,
        Long workspaceId,
        Long createdByMemberId,
        String title,
        String method,
        String endpoint,
        String groupName,
        String entityName,
        String summary,
        String description,
        String status,
        Long assigneeId,
        String pathParams,
        String headers,
        String queryParams,
        String requestBody,
        String responseBody,
        Integer responseStatus,
        String version,
        String sourceType,
        Long relatedIssueId,
        Long relatedPrId,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ApiSpecResponse from(ApiSpec spec) {
        return new ApiSpecResponse(
                spec.getId(),
                spec.getWorkspace().getId(),
                spec.getCreatedBy().getId(),
                spec.getTitle(),
                spec.getMethod(),
                spec.getEndpoint(),
                spec.getGroupName(),
                spec.getEntity(),
                spec.getSummary(),
                spec.getDescription(),
                spec.getStatus(),
                spec.getAssignee() == null ? null : spec.getAssignee().getId(),
                spec.getPathParams(),
                spec.getHeaders(),
                spec.getQueryParams(),
                spec.getRequestBody(),
                spec.getResponseBody(),
                spec.getResponseStatus(),
                spec.getVersion(),
                spec.getSourceType(),
                spec.getRelatedIssue() == null ? null : spec.getRelatedIssue().getId(),
                spec.getRelatedPr() == null ? null : spec.getRelatedPr().getId(),
                spec.getNote(),
                spec.getCreatedAt(),
                spec.getUpdatedAt()
        );
    }
}
