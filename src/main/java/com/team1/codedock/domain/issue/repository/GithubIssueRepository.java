package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GithubIssueRepository extends JpaRepository<GithubIssue, Long> {

    Optional<GithubIssue> findByIdAndRepository_Workspace_Id(Long id, Long workspaceId);

    @Query("SELECT COUNT(gi) FROM GithubIssue gi WHERE gi.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);
}
