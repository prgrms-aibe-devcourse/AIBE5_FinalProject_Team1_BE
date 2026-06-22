package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelMessageCreateRequest(
        @NotBlank @Size(max = 4000) String content,
        Long replyToMessageId,
        @Size(max = 64) String clientMessageId
) {
    public ChannelMessageCreateRequest(String content) {
        this(content, null, null);
    }

    public ChannelMessageCreateRequest(String content, Long replyToMessageId) {
        this(content, replyToMessageId, null);
    }
}
