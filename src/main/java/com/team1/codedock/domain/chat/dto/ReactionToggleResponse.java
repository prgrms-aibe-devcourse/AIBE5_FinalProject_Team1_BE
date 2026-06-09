package com.team1.codedock.domain.chat.dto;

public record ReactionToggleResponse(
        Long channelId,
        Long workspaceMemberId,
        String targetType,
        Long targetId,
        String emoji,
        boolean reacted,
        long count
) {
    public static ReactionToggleResponse of(
            Long channelId,
            Long workspaceMemberId,
            String targetType,
            Long targetId,
            String emoji,
            boolean reacted,
            long count
    ) {
        return new ReactionToggleResponse(
                channelId,
                workspaceMemberId,
                targetType,
                targetId,
                emoji,
                reacted,
                count
        );
    }
}
