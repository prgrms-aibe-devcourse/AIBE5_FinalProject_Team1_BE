package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_review_comments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestReviewComment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_review_comments")
    @SequenceGenerator(name = "seq_pr_review_comments", sequenceName = "seq_pr_review_comments", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "diff_line_id")
    private PullRequestDiffLine diffLine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_reply_id")
    private ThreadReply threadReply;

    @Lob
    @Column(nullable = false)
    private String content;
}
