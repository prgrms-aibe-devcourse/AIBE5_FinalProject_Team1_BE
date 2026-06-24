package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.IssueAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface IssueAssigneeRepository extends JpaRepository<IssueAssignee, Long> {
    List<IssueAssignee> findAllByGithubIssue_Id(Long issueId);
    void deleteAllByGithubIssue_Id(Long issueId);

    @Query("SELECT COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id = :workspaceId AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open'")
    long countOpenByUserIdAndWorkspaceId(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id IN :workspaceIds AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open'")
    long countOpenByUserIdAndWorkspaceIdIn(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);

    @Query("SELECT ia.workspaceMember.workspace.id, COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id IN :workspaceIds AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open' GROUP BY ia.workspaceMember.workspace.id")
    List<Object[]> countOpenGroupByWorkspaceId(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);
}
