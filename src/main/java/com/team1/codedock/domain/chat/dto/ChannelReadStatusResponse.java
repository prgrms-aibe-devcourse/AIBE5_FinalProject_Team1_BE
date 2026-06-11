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
    // 클라이언트에 필요한 읽음 상태 필드만 내려줌
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
