package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import com.team1.codedock.domain.chat.entity.Thread;

import java.time.LocalDateTime;

public record ChannelReadStatusResponse(
        Long channelId,
        Long workspaceMemberId,
        Long lastReadThreadId,
        LocalDateTime lastReadAt
) {
    // API 응답에서는 연관 엔티티 전체가 아니라 프론트가 필요한 식별자와 읽음 시간만 내려줌
    public static ChannelReadStatusResponse from(ChannelReadStatus status) {
        Thread lastReadThread = status.getLastReadThread();

        return new ChannelReadStatusResponse(
                status.getChannel().getId(),
                status.getWorkspaceMember().getId(),
                lastReadThread == null ? null : lastReadThread.getId(),
                status.getLastReadAt()
        );
    }
}
