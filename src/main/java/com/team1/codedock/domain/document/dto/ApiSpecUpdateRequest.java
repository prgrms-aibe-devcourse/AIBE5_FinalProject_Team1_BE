package com.team1.codedock.domain.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApiSpecUpdateRequest(
        // null 허용(미전송 시 기존 값 유지), 전송 시 blank 불가
        @NotBlank @Size(max = 255) String title,
        @NotBlank @Size(max = 10) String method,
        @NotBlank @Size(max = 255) String endpoint,
        @Size(max = 100) String groupName,
        @Size(max = 100) String entityName,
        @Size(max = 255) String summary,
        String description,
        // 'completed' | 'in_progress' | 'design'
        @Size(max = 30) String status,
        Long assigneeId,
        String pathParams,
        String headers,
        String queryParams,
        String requestBody,
        String responseBody,
        Integer responseStatus,
        @Size(max = 50) String version,
        // 'manual' | 'github' | 'imported'
        @Size(max = 30) String sourceType,
        Long relatedIssueId,
        Long relatedPrId,
        String note
) {}
