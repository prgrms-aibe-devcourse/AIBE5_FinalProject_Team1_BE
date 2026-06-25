package com.team1.codedock.domain.github.dto;

import java.time.LocalDateTime;
import java.util.List;

public record GithubRepositoryOverviewResponse(
        Long repositoryId,
        Long workspaceId,
        Long channelId,
        String owner,
        String name,
        String fullName,
        String url,
        String defaultBranch,
        LocalDateTime lastSyncedAt,
        long todayCommitCount,
        long openPrCount,
        long openIssueCount,
        long highRiskCount,
        long activeMemberCount,
        Integer codeQualityScore,
        Integer securityScore,
        Integer performanceScore,
        List<RepositoryActivityResponse> recentActivities,
        List<RepositoryPullRequestSummaryResponse> openPullRequests
) {

    public record RepositoryActivityResponse(
            String type,
            Long id,
            Integer number,
            String title,
            String actor,
            String state,
            LocalDateTime occurredAt
    ) {
    }

    public record RepositoryPullRequestSummaryResponse(
            Long prId,
            Integer prNumber,
            String title,
            String author,
            String state,
            Integer changedFilesCount,
            Integer additions,
            Integer deletions,
            LocalDateTime updatedAt
    ) {
    }
}
