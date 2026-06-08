package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TypingEventRequest(
        @NotNull Long workspaceMemberId,
        @NotBlank @Size(max = 100) String senderName,
        @NotNull Boolean typing
) {
}
