package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PullRequestReviewRequestRepository extends JpaRepository<PullRequestReviewRequest, Long> {

    @Query("SELECT COUNT(rr) FROM PullRequestReviewRequest rr WHERE rr.workspaceMember.user.id = :userId AND rr.workspaceMember.workspace.id = :workspaceId")
    long countByUserIdAndWorkspaceId(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);
}
