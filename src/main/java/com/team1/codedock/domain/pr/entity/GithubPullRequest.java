package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_pull_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubPullRequest extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_github_prs")
    @SequenceGenerator(name = "seq_github_prs", sequenceName = "seq_github_prs", allocationSize = 1)
    private Long id;

    @Column(name = "github_pr_id", nullable = false, length = 100)
    private String githubPrId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GithubRepository repository;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "channel_id", nullable = false)
    private Channel channel;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column
    private String description;

    // 'open' | 'merged' | 'closed'
    @Column(nullable = false, length = 50)
    private String state;

    @Lob
    @Column(nullable = false)
    private String url;

    @Column(length = 100)
    private String author;

    @Column(name = "head_branch", length = 255)
    private String headBranch;

    @Column(name = "base_branch", length = 255)
    private String baseBranch;

    // JSONB -> CLOB (GitHub 원본 스냅샷)
    @Lob
    @Column
    private String labels;

    @Column(nullable = false)
    private Integer additions;

    @Column(nullable = false)
    private Integer deletions;

    @Column(name = "changed_files_count", nullable = false)
    private Integer changedFilesCount;

    @Column(name = "merged_at")
    private LocalDateTime mergedAt;

    @Column(name = "github_created_at")
    private LocalDateTime githubCreatedAt;

    @Column(name = "github_updated_at")
    private LocalDateTime githubUpdatedAt;

    @Lob
    @Column(name = "commits_json")
    private String commitsJson;

    public static GithubPullRequest create(
            GithubRepository repository,
            Channel channel,
            String githubPrId,
            Integer prNumber,
            String title,
            String description,
            String state,
            String url,
            String author,
            String headBranch,
            String baseBranch,
            String labels,
            Integer additions,
            Integer deletions,
            Integer changedFilesCount,
            LocalDateTime mergedAt,
            LocalDateTime githubCreatedAt,
            LocalDateTime githubUpdatedAt,
            String commitsJson
    ) {
        GithubPullRequest pr = new GithubPullRequest();
        pr.repository = repository;
        pr.channel = channel;
        pr.githubPrId = githubPrId;
        pr.prNumber = prNumber;
        pr.title = title;
        pr.description = description;
        pr.state = state;
        pr.url = url;
        pr.author = author;
        pr.headBranch = headBranch;
        pr.baseBranch = baseBranch;
        pr.labels = labels;
        pr.additions = additions != null ? additions : 0;
        pr.deletions = deletions != null ? deletions : 0;
        pr.changedFilesCount = changedFilesCount != null ? changedFilesCount : 0;
        pr.mergedAt = mergedAt;
        pr.githubCreatedAt = githubCreatedAt;
        pr.githubUpdatedAt = githubUpdatedAt;
        pr.commitsJson = commitsJson;
        return pr;
    }

    public void updateCommits(String commitsJson) {
        this.commitsJson = commitsJson;
    }

    public void updateDescription(String description) {
        this.description = description;
    }

    public void updateState(String state) {
        this.state = state;
    }
}
