package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ThreadReplyCreateRequest(
        @NotBlank
        @Size(max = 4000)
        String content
) {
}
