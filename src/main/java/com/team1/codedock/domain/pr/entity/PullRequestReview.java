package com.team1.codedock.domain.pr.entity;

import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pull_request_reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PullRequestReview extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_pr_reviews")
    @SequenceGenerator(name = "seq_pr_reviews", sequenceName = "seq_pr_reviews", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_pull_request_id", nullable = false)
    private GithubPullRequest githubPullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false)
    private WorkspaceMember workspaceMember;

    @Column(name = "github_review_id", length = 100)
    private String githubReviewId;

    // 'approved' | 'changes_requested' | 'commented'
    @Column(name = "review_state", nullable = false, length = 50)
    private String reviewState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "thread_reply_id")
    private ThreadReply threadReply;
}
