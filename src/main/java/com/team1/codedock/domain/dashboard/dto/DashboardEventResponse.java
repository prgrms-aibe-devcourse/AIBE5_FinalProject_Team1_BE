package com.team1.codedock.domain.dashboard.dto;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;

import java.time.LocalDateTime;

public record DashboardEventResponse(
        Long eventId,
        String type,
        Long workspaceId,
        String workspaceName,
        String actorName,
        Long prId,
        Long issueId,
        Long channelId,
        Long repositoryId,
        String repositoryName,
        Long threadId,
        Long prNumber,
        Long issueNumber,
        String content,
        boolean isRead,
        LocalDateTime createdAt,
        LocalDateTime occurredAt,
        String navigationType
) {
    public static DashboardEventResponse from(WorkspaceEvent event, boolean isRead) {
        return new DashboardEventResponse(
                event.getId(),
                event.getType().name(),
                event.getWorkspace().getId(),
                event.getWorkspace().getName(),
                event.getActorName(),
                event.getPrId(),
                event.getIssueId(),
                event.getChannelId(),
                event.getRepositoryId(),
                event.getRepositoryName(),
                event.getThreadId(),
                event.getPrNumber(),
                event.getIssueNumber(),
                event.getContent(),
                isRead,
                event.getCreatedAt(),
                event.getDisplayOccurredAt(),
                resolveNavigationType(event)
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
