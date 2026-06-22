package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GithubPullRequestRepository extends JpaRepository<GithubPullRequest, Long> {

    Optional<GithubPullRequest> findByIdAndRepository_Workspace_Id(Long id, Long workspaceId);

    Optional<GithubPullRequest> findByRepository_IdAndGithubPrId(Long repositoryId, String githubPrId);

    List<GithubPullRequest> findAllByRepository_IdOrderByGithubCreatedAtDesc(Long repositoryId);

    @Query("SELECT COUNT(pr) FROM GithubPullRequest pr WHERE pr.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);
}
