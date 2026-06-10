package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ThreadReplyWebSocketCreateRequest(
        @NotNull Long userId,
        @NotBlank @Size(max = 4000) String content
) {
    public ThreadReplyCreateRequest toCreateRequest() {
        return new ThreadReplyCreateRequest(content);
    }
}
