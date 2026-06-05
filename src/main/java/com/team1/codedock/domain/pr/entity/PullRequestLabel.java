package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_labels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestLabel extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_labels")
    @SequenceGenerator(name = "seq_pr_labels", sequenceName = "seq_pr_labels", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;
}
