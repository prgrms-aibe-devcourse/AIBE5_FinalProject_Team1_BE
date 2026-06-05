package com.team1.codedock.domain.issue.entity;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubIssue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_github_issues")
    @SequenceGenerator(name = "seq_github_issues", sequenceName = "seq_github_issues", allocationSize = 1)
    private Long id;

    @Column(name = "github_issue_id", nullable = false, length = 100)
    private String githubIssueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GithubRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "issue_number", nullable = false)
    private Integer issueNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column
    private String description;

    // 'open' | 'closed'
    @Column(nullable = false, length = 50)
    private String state;

    // 'todo' | 'in_progress' | 'review' | 'done' | 'blocked'
    @Column(name = "local_status", length = 50)
    private String localStatus;

    @Lob
    @Column(nullable = false)
    private String url;

    @Column(length = 100)
    private String author;

    // 'high' | 'medium' | 'low'
    @Column(length = 20)
    private String priority;

    @Column(name = "issue_type", length = 50)
    private String issueType;

    // JSONB -> CLOB (GitHub 원본 스냅샷)
    @Lob
    @Column
    private String labels;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "github_created_at")
    private LocalDateTime githubCreatedAt;

    @Column(name = "github_updated_at")
    private LocalDateTime githubUpdatedAt;
}
