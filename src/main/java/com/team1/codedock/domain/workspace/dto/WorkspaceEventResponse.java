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
        Long threadId,
        Long prNumber,
        Long issueNumber
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
                event.getThreadId(),
                event.getPrNumber(),
                event.getIssueNumber()
        );
    }
}
