package com.team1.codedock.domain.dashboard.dto;

public record WorkspaceDashboardResponse(
        Long workspaceId,
        String workspaceName,
        String workspaceLogoUrl,
        long openIssueCount,
        long openPrCount,
        long reviewRequestCount,
        long receivedReviewCount
) {}
