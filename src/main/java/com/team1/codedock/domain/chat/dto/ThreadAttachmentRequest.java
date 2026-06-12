package com.team1.codedock.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ThreadAttachmentRequest(
        @NotBlank
        @Size(max = 30)
        @JsonAlias("type")
        String attachmentType,

        Long targetId,

        String url,

        @Size(max = 255)
        String title,

        @Size(max = 255)
        String detail,

        @Size(max = 100)
        String meta,

        String previewUrl,

        @Size(max = 100)
        String mimeType,

        @PositiveOrZero
        @JsonAlias("size")
        Long fileSize
) {
}
