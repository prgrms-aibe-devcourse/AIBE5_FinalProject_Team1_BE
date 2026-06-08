package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChannelMessageCreateRequest(
        @NotNull Long senderMemberId,
        @NotBlank @Size(max = 4000) String content
) {
}
