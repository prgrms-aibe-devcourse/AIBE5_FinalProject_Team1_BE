package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubIssueRepository extends JpaRepository<GithubIssue, Long> {

    Optional<GithubIssue> findByIdAndRepository_Workspace_Id(Long id, Long workspaceId);

    boolean existsByChannel_Id(Long channelId);
}
