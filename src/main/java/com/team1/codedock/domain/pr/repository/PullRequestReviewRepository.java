package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    Optional<PullRequestReview> findByGithubPullRequest_IdAndWorkspaceMember_Id(
            Long pullRequestId, Long workspaceMemberId);

    long countByGithubPullRequest_IdAndReviewState(Long pullRequestId, String reviewState);

    @Query("SELECT COUNT(r) FROM PullRequestReview r WHERE r.githubPullRequest.author = :author AND r.githubPullRequest.repository.workspace.id = :workspaceId AND r.githubPullRequest.state = 'open'")
    long countOnOpenPrsByAuthorAndWorkspaceId(@Param("author") String author, @Param("workspaceId") Long workspaceId);
}
