package com.team1.codedock.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ThreadAttachmentRequest(
        @NotBlank(message = "첨부파일 타입은 필수입니다.")
        @Size(max = 30, message = "첨부파일 타입은 30자 이하로 입력해주세요.")
        @JsonAlias("type")
        String attachmentType,

        Long targetId,

        String url,

        @Size(max = 255, message = "첨부파일 제목은 255자 이하로 입력해주세요.")
        String title,

        @Size(max = 255, message = "첨부파일 설명은 255자 이하로 입력해주세요.")
        String detail,

        @Size(max = 100, message = "첨부파일 메타 정보는 100자 이하로 입력해주세요.")
        String meta,

        String previewUrl,

        @Size(max = 100, message = "첨부파일 MIME 타입은 100자 이하로 입력해주세요.")
        String mimeType,

        @PositiveOrZero(message = "첨부파일 크기는 0 이상이어야 합니다.")
        @JsonAlias("size")
        Long fileSize
) {
}
