package com.team1.codedock.domain.channel.dto;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;

import java.time.LocalDateTime;

public record ChannelListResponse(
        Long id,
        Long workspaceId,
        Long githubRepositoryId,
        String name,
        String channelType,
        boolean isDeletable,
        String description,
        String lastMessage,
        LocalDateTime lastMessageAt,
        long messageCount,
        // 현재 멤버가 아직 읽지 않은 채널 메시지 수임
        long unreadCount
) {
    public static ChannelListResponse from(Channel channel) {
        return from(channel, null, null, 0L, 0L);
    }

    public static ChannelListResponse from(
            Channel channel,
            String lastMessage,
            LocalDateTime lastMessageAt,
            long messageCount,
            long unreadCount
    ) {
        GithubRepository githubRepository = channel.getGithubRepository();

        return new ChannelListResponse(
                channel.getId(),
                channel.getWorkspace().getId(),
                githubRepository == null ? null : githubRepository.getId(),
                channel.getName(),
                channel.getChannelType(),
                channel.isDeletable(),
                channel.getDescription(),
                lastMessage,
                lastMessageAt,
                messageCount,
                unreadCount
        );
    }
}
