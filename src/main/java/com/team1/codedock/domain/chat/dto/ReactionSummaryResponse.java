package com.team1.codedock.domain.chat.dto;

public record ReactionSummaryResponse(
        String targetType,
        Long targetId,
        String emoji,
        Long count
) {
}
