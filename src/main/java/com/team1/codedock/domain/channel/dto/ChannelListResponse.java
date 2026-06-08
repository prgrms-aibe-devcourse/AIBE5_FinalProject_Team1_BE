package com.team1.codedock.domain.channel.dto;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;

public record ChannelListResponse(
        Long id,
        Long workspaceId,
        Long githubRepositoryId,
        String name,
        String channelType,
        boolean isDeletable,
        String description
) {
    public static ChannelListResponse from(Channel channel) {
        GithubRepository githubRepository = channel.getGithubRepository();

        return new ChannelListResponse(
                channel.getId(),
                channel.getWorkspace().getId(),
                githubRepository == null ? null : githubRepository.getId(),
                channel.getName(),
                channel.getChannelType(),
                channel.isDeletable(),
                channel.getDescription()
        );
    }
}
