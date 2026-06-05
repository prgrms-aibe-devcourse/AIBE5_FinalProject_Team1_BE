package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_analysis")
    @SequenceGenerator(name = "seq_pr_analysis", sequenceName = "seq_pr_analysis", allocationSize = 1)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false, unique = true)
    private GithubPullRequest githubPullRequest;

    // 'Low' | 'Medium' | 'High'
    @Column(name = "risk_level", length = 10)
    private String riskLevel;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "review_room_active", nullable = false)
    private boolean reviewRoomActive;
}
