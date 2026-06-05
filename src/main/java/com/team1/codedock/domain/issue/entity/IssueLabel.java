package com.team1.codedock.domain.issue.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "issue_labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssueLabel extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_issue_labels")
    @SequenceGenerator(name = "seq_issue_labels", sequenceName = "seq_issue_labels", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_issue_id", nullable = false)
    private GithubIssue githubIssue;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;
}
