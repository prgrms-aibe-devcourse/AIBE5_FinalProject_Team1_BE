package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, Long> {
    List<IssueLabel> findAllByGithubIssue_Id(Long issueId);
    void deleteAllByGithubIssue_Id(Long issueId);
}
