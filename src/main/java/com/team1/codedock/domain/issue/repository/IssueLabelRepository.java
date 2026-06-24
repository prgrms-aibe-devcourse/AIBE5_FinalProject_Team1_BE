package com.team1.codedock.domain.issue.repository;

import com.team1.codedock.domain.issue.entity.IssueLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface IssueLabelRepository extends JpaRepository<IssueLabel, Long> {
    List<IssueLabel> findAllByGithubIssue_Id(Long issueId);

    // 목록 조회 시 이슈별 라벨을 한 번에 가져와 N+1을 제거하기 위한 배치 조회.
    List<IssueLabel> findAllByGithubIssue_IdIn(Collection<Long> issueIds);

    void deleteAllByGithubIssue_Id(Long issueId);
}
