package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;

import java.time.LocalDateTime;

public record WorkspaceEventResponse(
        Long id,
        Long workspaceId,
        String type,
        String actorName,
        Long prId,
        Long issueId,
        Long channelId,
        String content,
        LocalDateTime createdAt,
        Long repositoryId,
        String repositoryName,
        Long threadId,
        Long prNumber,
        Long issueNumber,
        boolean isRead
) {
    public static WorkspaceEventResponse from(WorkspaceEvent event) {
        return new WorkspaceEventResponse(
                event.getId(),
                event.getWorkspace().getId(),
                event.getType().name(),
                event.getActorName(),
                event.getPrId(),
                event.getIssueId(),
                event.getChannelId(),
                event.getContent(),
                event.getCreatedAt(),
                event.getRepositoryId(),
                event.getRepositoryName(),
                event.getThreadId(),
                event.getPrNumber(),
                event.getIssueNumber(),
                event.isRead()
        );
    }
}
