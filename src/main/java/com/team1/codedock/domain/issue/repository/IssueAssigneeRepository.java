package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.IssueAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface IssueAssigneeRepository extends JpaRepository<IssueAssignee, Long> {
    List<IssueAssignee> findAllByGithubIssue_Id(Long issueId);

    // 목록 조회 시 이슈별 담당자를 한 번에 가져온다. DTO가 담당자명을 위해 user를 읽으므로
    // workspaceMember.user까지 fetch join 해 매핑 단계의 추가 N+1을 막는다.
    @Query("""
        SELECT ia FROM IssueAssignee ia
        JOIN FETCH ia.workspaceMember wm
        JOIN FETCH wm.user
        WHERE ia.githubIssue.id IN :issueIds
        """)
    List<IssueAssignee> findAllByIssueIdInFetchUser(@Param("issueIds") Collection<Long> issueIds);

    void deleteAllByGithubIssue_Id(Long issueId);

    @Query("SELECT COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id = :workspaceId AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open'")
    long countOpenByUserIdAndWorkspaceId(@Param("userId") Long userId, @Param("workspaceId") Long workspaceId);

    @Query("SELECT COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id IN :workspaceIds AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open'")
    long countOpenByUserIdAndWorkspaceIdIn(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);

    @Query("SELECT ia.workspaceMember.workspace.id, COUNT(ia) FROM IssueAssignee ia WHERE ia.workspaceMember.user.id = :userId AND ia.workspaceMember.workspace.id IN :workspaceIds AND ia.workspaceMember.isActive = true AND ia.githubIssue.state = 'open' GROUP BY ia.workspaceMember.workspace.id")
    List<Object[]> countOpenGroupByWorkspaceId(@Param("userId") Long userId, @Param("workspaceIds") List<Long> workspaceIds);
}
