package com.team1.codedock.domain.dashboard.dto;

public record DashboardSummaryResponse(
        long openIssueCount,
        long openPrCount,
        long reviewRequestCount,
        long receivedReviewCount
) {
    public static DashboardSummaryResponse of(long openIssueCount, long openPrCount, long reviewRequestCount, long receivedReviewCount) {
        return new DashboardSummaryResponse(openIssueCount, openPrCount, reviewRequestCount, receivedReviewCount);
    }
}
