package com.team1.codedock.domain.chat.dto;

public record BookmarkToggleResponse(
        Long channelId,
        Long messageId,
        Long workspaceMemberId,
        boolean bookmarked
) {
    public static BookmarkToggleResponse of(
            Long channelId,
            Long messageId,
            Long workspaceMemberId,
            boolean bookmarked
    ) {
        return new BookmarkToggleResponse(
                channelId,
                messageId,
                workspaceMemberId,
                bookmarked
        );
    }
}
