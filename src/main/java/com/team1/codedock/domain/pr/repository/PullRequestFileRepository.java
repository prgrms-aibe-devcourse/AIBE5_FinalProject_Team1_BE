package com.team1.codedock.domain.pr.repository;

import com.team1.codedock.domain.pr.entity.PullRequestFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PullRequestFileRepository extends JpaRepository<PullRequestFile, Long> {

    List<PullRequestFile> findAllByGithubPullRequest_Id(Long pullRequestId);
}
