package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    Optional<PullRequestReview> findByGithubPullRequest_IdAndWorkspaceMember_Id(
            Long pullRequestId, Long workspaceMemberId);

    long countByGithubPullRequest_IdAndReviewState(Long pullRequestId, String reviewState);

    // PR 목록 조회 시 PR별 특정 상태 리뷰 수를 한 번에 집계해 N+1을 제거한다.
    // 반환: [pullRequestId, count] 행 목록.
    @Query("""
        SELECT r.githubPullRequest.id, COUNT(r) FROM PullRequestReview r
        WHERE r.githubPullRequest.id IN :pullRequestIds AND r.reviewState = :reviewState
        GROUP BY r.githubPullRequest.id
        """)
    List<Object[]> countByPullRequestIdInAndReviewStateGroupByPr(
            @Param("pullRequestIds") Collection<Long> pullRequestIds,
            @Param("reviewState") String reviewState);

    @Query("SELECT COUNT(r) FROM PullRequestReview r WHERE r.githubPullRequest.author = :author AND r.githubPullRequest.repository.workspace.id = :workspaceId AND r.githubPullRequest.state IN ('open', 'approved')")
    long countOnOpenPrsByAuthorAndWorkspaceId(@Param("author") String author, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COUNT(r) FROM PullRequestReview r WHERE r.githubPullRequest.author = :author AND r.githubPullRequest.repository.workspace.id IN :workspaceIds AND r.githubPullRequest.state IN ('open', 'approved')")
    long countOnOpenPrsByAuthorAndWorkspaceIdIn(@Param("author") String author, @Param("workspaceIds") List<Long> workspaceIds);

    @Query("SELECT r.githubPullRequest.repository.workspace.id, COUNT(r) FROM PullRequestReview r WHERE r.githubPullRequest.author = :author AND r.githubPullRequest.repository.workspace.id IN :workspaceIds AND r.githubPullRequest.state IN ('open', 'approved') GROUP BY r.githubPullRequest.repository.workspace.id")
    List<Object[]> countOnOpenPrsGroupByWorkspaceId(@Param("author") String author, @Param("workspaceIds") List<Long> workspaceIds);
}
