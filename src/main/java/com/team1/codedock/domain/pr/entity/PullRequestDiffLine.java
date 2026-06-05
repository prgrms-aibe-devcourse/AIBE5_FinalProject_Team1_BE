package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_diff_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestDiffLine extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_diff_lines")
    @SequenceGenerator(name = "seq_pr_diff_lines", sequenceName = "seq_pr_diff_lines", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_file_id", nullable = false)
    private PullRequestFile pullRequestFile;

    // 'added' | 'removed' | 'context'
    @Column(name = "line_type", nullable = false, length = 20)
    private String lineType;

    @Column(name = "old_line_number")
    private Integer oldLineNumber;

    @Column(name = "new_line_number")
    private Integer newLineNumber;

    @Lob
    @Column(nullable = false)
    private String content;
}
