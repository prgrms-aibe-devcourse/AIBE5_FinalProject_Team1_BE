package com.team1.codedock.domain.chat.dto;

import java.time.LocalDateTime;

// /user/queue/notifications payload로 내려갈 개인 알림 정보임
public record ChatNotificationResponse(
        Long workspaceId,
        Long channelId,
        Long threadId,
        Long threadReplyId,
        Long mentionedMemberId,
        String message,
        LocalDateTime createdAt
) {
    public static ChatNotificationResponse of(
            Long workspaceId,
            Long channelId,
            Long threadId,
            Long threadReplyId,
            Long mentionedMemberId,
            String message
    ) {
        return new ChatNotificationResponse(
                workspaceId,
                channelId,
                threadId,
                threadReplyId,
                mentionedMemberId,
                message,
                LocalDateTime.now()
        );
    }
}
