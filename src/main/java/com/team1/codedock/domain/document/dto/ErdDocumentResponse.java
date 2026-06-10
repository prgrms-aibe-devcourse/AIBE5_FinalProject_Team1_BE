package com.team1.codedock.domain.document.dto;

import com.team1.codedock.domain.document.entity.ErdDocument;

import java.time.LocalDateTime;

public record ErdDocumentResponse(
        Long id,
        Long workspaceId,
        Long createdByMemberId,
        String title,
        String description,
        String mermaidCode,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ErdDocumentResponse from(ErdDocument doc) {
        return new ErdDocumentResponse(
                doc.getId(),
                doc.getWorkspace().getId(),
                doc.getCreatedBy().getId(),
                doc.getTitle(),
                doc.getDescription(),
                doc.getMermaidCode(),
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }
}
