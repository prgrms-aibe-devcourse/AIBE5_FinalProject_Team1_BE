package com.team1.codedock.domain.chat.dto;

public record ReactionSummaryResponse(
        String targetType,
        Long targetId,
        String emoji,
        Long count,
        boolean reacted
) {

    public ReactionSummaryResponse(String targetType, Long targetId, String emoji, Long count) {
        this(targetType, targetId, emoji, count, false);
    }

    public ReactionSummaryResponse withReacted(boolean reacted) {
        return new ReactionSummaryResponse(targetType, targetId, emoji, count, reacted);
    }
}
