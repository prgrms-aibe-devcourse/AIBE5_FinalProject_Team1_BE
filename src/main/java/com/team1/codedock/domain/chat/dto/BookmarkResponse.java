package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.Bookmark;

import java.time.LocalDateTime;

public record BookmarkResponse(
        Long bookmarkId,
        Long channelId,
        Long messageId,
        Long senderMemberId,
        String senderName,
        String content,
        LocalDateTime messageCreatedAt,
        LocalDateTime bookmarkedAt
) {
    // 북마크 메타데이터와 메시지 요약 정보를 함께 내려줌
    public static BookmarkResponse from(Bookmark bookmark) {
        ChannelMessageResponse message = ChannelMessageResponse.from(bookmark.getThread());

        return new BookmarkResponse(
                bookmark.getId(),
                message.channelId(),
                message.id(),
                message.senderMemberId(),
                message.senderName(),
                message.content(),
                message.createdAt(),
                bookmark.getCreatedAt()
        );
    }
}
