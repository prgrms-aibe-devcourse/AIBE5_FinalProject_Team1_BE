package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelMessageCreateRequest(
        @NotBlank @Size(max = 4000) String content,
        Long replyToMessageId
) {
    public ChannelMessageCreateRequest(String content) {
        this(content, null);
    }
}
