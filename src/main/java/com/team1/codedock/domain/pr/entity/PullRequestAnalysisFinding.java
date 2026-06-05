package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_analysis_findings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestAnalysisFinding extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_findings")
    @SequenceGenerator(name = "seq_pr_findings", sequenceName = "seq_pr_findings", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_analysis_id", nullable = false)
    private PullRequestAnalysis pullRequestAnalysis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_file_id")
    private PullRequestFile pullRequestFile;

    // 'security' | 'performance' | 'style'
    @Column(name = "finding_type", nullable = false, length = 50)
    private String findingType;

    // 'high' | 'medium' | 'low'
    @Column(nullable = false, length = 20)
    private String severity;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Lob
    @Column(nullable = false)
    private String description;

    @Lob
    @Column
    private String suggestion;
}
