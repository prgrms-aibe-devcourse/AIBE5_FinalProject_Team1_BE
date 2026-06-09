package com.team1.codedock.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReactionToggleRequest(
        @NotNull Long workspaceMemberId,
        @NotBlank @Size(max = 30) String targetType,
        @NotNull Long targetId,
        @NotBlank @Size(max = 50) String emoji
) {
}
