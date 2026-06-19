package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.IssueAssignee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IssueAssigneeRepository extends JpaRepository<IssueAssignee, Long> {
    List<IssueAssignee> findAllByGithubIssue_Id(Long issueId);
    void deleteAllByGithubIssue_Id(Long issueId);
}
