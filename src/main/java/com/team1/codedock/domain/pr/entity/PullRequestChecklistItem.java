package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_checklist_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestChecklistItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_checklist")
    @SequenceGenerator(name = "seq_pr_checklist", sequenceName = "seq_pr_checklist", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "is_checked", nullable = false)
    private boolean isChecked;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private WorkspaceMember createdBy;
}
