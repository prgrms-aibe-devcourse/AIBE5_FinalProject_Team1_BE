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
        LocalDateTime occurredAt,
        String navigationType,
        Long repositoryId,
        String repositoryName,
        Long threadId,
        Long prNumber,
        Long issueNumber,
        boolean isRead
) {
    public static WorkspaceEventResponse from(WorkspaceEvent event, boolean isRead) {
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
                event.getDisplayOccurredAt(),
                resolveNavigationType(event),
                event.getRepositoryId(),
                event.getRepositoryName(),
                event.getThreadId(),
                event.getPrNumber(),
                event.getIssueNumber(),
                isRead
        );
    }

    private static String resolveNavigationType(WorkspaceEvent event) {
        return switch (event.getType()) {
            case PR_CREATED, PR_REVIEW -> event.getPrId() != null ? "PR" : fallbackNavigationType(event);
            case ISSUE_CREATED -> event.getIssueId() != null ? "ISSUE" : fallbackNavigationType(event);
            case REPLY -> event.getThreadId() != null ? "THREAD" : fallbackNavigationType(event);
            case MENTION -> event.getThreadId() != null ? "MENTION" : fallbackNavigationType(event);
        };
    }

    private static String fallbackNavigationType(WorkspaceEvent event) {
        return event.getChannelId() != null ? "CHANNEL" : "WORKSPACE";
    }
}
