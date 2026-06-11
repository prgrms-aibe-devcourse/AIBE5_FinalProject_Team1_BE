package com.team1.codedock.domain.ai.entity;

import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "ai_summaries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSummary extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ai_summaries")
    @SequenceGenerator(name = "seq_ai_summaries", sequenceName = "seq_ai_summaries", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_issue_id")
    private GithubIssue githubIssue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id")
    private GithubPullRequest githubPullRequest;

    @Lob
    @Column
    private String summary;

    // 'Low' | 'Medium' | 'High'
    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    // 'pending' | 'processing' | 'completed' | 'failed'
    @Column(nullable = false, length = 30)
    private String status = "pending";

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    public static AiSummary create(GithubPullRequest pr) {
        AiSummary aiSummary = new AiSummary();
        aiSummary.githubPullRequest = pr;
        aiSummary.status = "pending";
        return aiSummary;
    }

    public void startProcessing() {
        this.status = "processing";
    }

    public void complete(String summary, String riskLevel, String modelVersion) {
        this.summary = summary;
        this.riskLevel = riskLevel;
        this.modelVersion = modelVersion;
        this.status = "completed";
    }

    public void fail() {
        this.status = "failed";
    }
}
