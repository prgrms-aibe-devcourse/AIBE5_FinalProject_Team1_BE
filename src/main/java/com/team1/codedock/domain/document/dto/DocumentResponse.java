package com.team1.codedock.domain.document.dto;

import com.team1.codedock.domain.document.entity.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long workspaceId,
        Long createdByMemberId,
        String title,
        String content,
        String category,
        String generatedBy,
        String visibility,
        Long relatedPrId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getWorkspace().getId(),
                document.getCreatedBy().getId(),
                document.getTitle(),
                document.getContent(),
                document.getCategory(),
                document.getGeneratedBy(),
                document.getVisibility(),
                document.getRelatedPr() == null ? null : document.getRelatedPr().getId(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
