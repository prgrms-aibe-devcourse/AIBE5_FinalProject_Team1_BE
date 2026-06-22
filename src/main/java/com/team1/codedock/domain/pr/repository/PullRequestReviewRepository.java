package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    Optional<PullRequestReview> findByGithubPullRequest_IdAndWorkspaceMember_Id(
            Long pullRequestId, Long workspaceMemberId);

    long countByGithubPullRequest_IdAndReviewState(Long pullRequestId, String reviewState);
}
