package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubPullRequestRepository extends JpaRepository<GithubPullRequest, Long> {

    Optional<GithubPullRequest> findByIdAndRepository_Workspace_Id(Long id, Long workspaceId);
}
