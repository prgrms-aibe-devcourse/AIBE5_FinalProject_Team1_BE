package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestReviewRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PullRequestReviewRequestRepository extends JpaRepository<PullRequestReviewRequest, Long> {

    @Query("SELECT COUNT(rr) FROM PullRequestReviewRequest rr WHERE rr.workspaceMember.user.id = :userId AND rr.workspaceMember.workspace.id = :workspaceId AND rr.githubPullRequest.state IN ('open', 'approved')")
    long countByUserIdAndWorkspaceId(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COUNT(rr) FROM PullRequestReviewRequest rr WHERE rr.workspaceMember.user.id = :userId AND rr.workspaceMember.workspace.id IN :workspaceIds AND rr.githubPullRequest.state IN ('open', 'approved')")
    long countByUserIdAndWorkspaceIdIn(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);

    @Query("SELECT rr.workspaceMember.workspace.id, COUNT(rr) FROM PullRequestReviewRequest rr WHERE rr.workspaceMember.user.id = :userId AND rr.workspaceMember.workspace.id IN :workspaceIds AND rr.githubPullRequest.state IN ('open', 'approved') GROUP BY rr.workspaceMember.workspace.id")
    List<Object[]> countGroupByWorkspaceId(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);

    void deleteAllByGithubPullRequest_Id(Long pullRequestId);
}
