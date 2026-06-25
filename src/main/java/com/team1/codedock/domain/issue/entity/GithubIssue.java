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

    private static final String STATE_CLOSED = "closed";
    private static final String LOCAL_STATUS_TODO = "todo";
    private static final String LOCAL_STATUS_DONE = "done";

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

    public static GithubIssue create(
            GithubRepository repository,
            Channel channel,
            String githubIssueId,
            Integer issueNumber,
            String title,
            String description,
            String state,
            String url,
            String author,
            String labels,
            LocalDateTime closedAt,
            LocalDateTime githubCreatedAt,
            LocalDateTime githubUpdatedAt
    ) {
        GithubIssue issue = new GithubIssue();
        issue.repository = repository;
        issue.channel = channel;
        issue.githubIssueId = githubIssueId;
        issue.issueNumber = issueNumber;
        issue.title = title;
        issue.description = description;
        issue.state = state;
        issue.localStatus = resolveInitialLocalStatus(state);
        issue.url = url;
        issue.author = author;
        issue.labels = labels;
        issue.closedAt = closedAt;
        issue.githubCreatedAt = githubCreatedAt;
        issue.githubUpdatedAt = githubUpdatedAt;
        return issue;
    }

    public void syncFromWebhook(
            String title,
            String description,
            String state,
            String url,
            String labels,
            LocalDateTime closedAt,
            LocalDateTime githubUpdatedAt
    ) {
        boolean wasClosed = isClosed();
        this.title = title;
        this.description = description;
        this.state = state;
        this.url = url;
        this.labels = labels;
        this.closedAt = closedAt;
        this.githubUpdatedAt = githubUpdatedAt;
        normalizeLocalStatusAfterGithubStateSync(wasClosed);
    }

    public boolean isClosed() {
        return STATE_CLOSED.equalsIgnoreCase(state);
    }

    public String getEffectiveLocalStatus() {
        if (isClosed()) {
            return LOCAL_STATUS_DONE;
        }

        if (localStatus == null || localStatus.isBlank()) {
            return LOCAL_STATUS_TODO;
        }

        return localStatus;
    }

    private void normalizeLocalStatusAfterGithubStateSync(boolean wasClosed) {
        if (isClosed()) {
            this.localStatus = LOCAL_STATUS_DONE;
            return;
        }

        // GitHub에서 다시 열린 이슈는 작업보드에 다시 나타나야 하므로,
        // 닫힘으로 인해 done이 되었던 항목만 todo로 되돌림.
        if (wasClosed && LOCAL_STATUS_DONE.equals(this.localStatus)) {
            this.localStatus = LOCAL_STATUS_TODO;
            return;
        }

        if (this.localStatus == null || this.localStatus.isBlank()) {
            this.localStatus = LOCAL_STATUS_TODO;
        }
    }

    public void updateLocalStatus(String localStatus) {
        this.localStatus = localStatus;
    }

    private static String resolveInitialLocalStatus(String state) {
        return STATE_CLOSED.equalsIgnoreCase(state) ? LOCAL_STATUS_DONE : LOCAL_STATUS_TODO;
    }

    // GitHub에서 가져온 분류 정보 반영. null이면 기존 값을 덮어쓰지 않는다(조회 실패 시 보존).
    public void applyClassification(String priority, String issueType) {
        if (priority != null && !priority.isBlank()) this.priority = priority;
        if (issueType != null && !issueType.isBlank()) this.issueType = issueType;
    }
}
