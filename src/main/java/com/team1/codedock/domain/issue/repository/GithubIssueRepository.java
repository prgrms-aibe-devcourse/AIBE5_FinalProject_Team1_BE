package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GithubIssueRepository extends JpaRepository<GithubIssue, Long> {

    Optional<GithubIssue> findByIdAndRepository_Workspace_Id(Long id, Long workspaceId);

    Optional<GithubIssue> findByRepository_IdAndGithubIssueId(Long repositoryId, String githubIssueId);

    @Query("SELECT COUNT(gi) FROM GithubIssue gi WHERE gi.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("""
        SELECT gi FROM GithubIssue gi
        WHERE gi.repository.workspace.id = :workspaceId
          AND (:repositoryId IS NULL OR gi.repository.id = :repositoryId)
          AND (:state IS NULL OR gi.state = :state)
          AND (:localStatus IS NULL OR gi.localStatus = :localStatus)
        ORDER BY gi.githubCreatedAt DESC
        """)
    Page<GithubIssue> findByWorkspaceWithFilters(
            @Param("workspaceId") Long workspaceId,
            @Param("repositoryId") Long repositoryId,
            @Param("state") String state,
            @Param("localStatus") String localStatus,
            Pageable pageable
    );

    // repository/channel은 DTO 매핑에서 즉시 사용되므로 fetch join 해 이슈별 지연 로딩 N+1을 막는다.
    @Query("""
        SELECT gi FROM GithubIssue gi
        JOIN FETCH gi.repository
        JOIN FETCH gi.channel
        WHERE gi.repository.workspace.id = :workspaceId
        ORDER BY gi.githubCreatedAt DESC
        """)
    List<GithubIssue> findAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
