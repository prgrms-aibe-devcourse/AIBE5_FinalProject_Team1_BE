package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.ThreadAttachment;

import java.time.LocalDateTime;

public record ThreadAttachmentResponse(
        Long id,
        String attachmentType,
        String type,
        Long targetId,
        String url,
        String title,
        String detail,
        String meta,
        String previewUrl,
        String mimeType,
        Long fileSize,
        Long size,
        LocalDateTime createdAt
) {

    public ThreadAttachmentResponse(
            Long id,
            String attachmentType,
            Long targetId,
            String url,
            String title,
            String detail,
            String meta,
            String previewUrl,
            String mimeType,
            Long fileSize,
            LocalDateTime createdAt
    ) {
        this(
                id,
                attachmentType,
                attachmentType,
                targetId,
                url,
                title,
                detail,
                meta,
                previewUrl,
                mimeType,
                fileSize,
                fileSize,
                createdAt
        );
    }

    public static ThreadAttachmentResponse from(ThreadAttachment attachment) {
        return new ThreadAttachmentResponse(
                attachment.getId(),
                attachment.getAttachmentType(),
                attachment.getAttachmentType(),
                attachment.getTargetId(),
                attachment.getUrl(),
                attachment.getTitle(),
                attachment.getDetail(),
                attachment.getMeta(),
                attachment.getPreviewUrl(),
                attachment.getMimeType(),
                attachment.getFileSize(),
                attachment.getFileSize(),
                attachment.getCreatedAt()
        );
    }
}
