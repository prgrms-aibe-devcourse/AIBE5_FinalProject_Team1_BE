package com.team1.codedock.domain.ai.repository;

import com.team1.codedock.domain.ai.entity.AiSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiSummaryRepository extends JpaRepository<AiSummary, Long> {

    Optional<AiSummary> findByGithubPullRequest_Id(Long pullRequestId);
}
