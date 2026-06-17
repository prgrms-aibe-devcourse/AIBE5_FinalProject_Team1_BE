package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreadReplyCreateRequest(
        @NotBlank(message = "답글 내용은 필수입니다.")
        @Size(max = 4000, message = "답글 내용은 4000자 이하로 입력해주세요.")
        String content
) {
}
