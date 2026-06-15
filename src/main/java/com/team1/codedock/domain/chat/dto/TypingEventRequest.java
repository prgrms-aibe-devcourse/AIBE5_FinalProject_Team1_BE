package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotNull;

public record TypingEventRequest(
        @NotNull Boolean typing
) {
}
