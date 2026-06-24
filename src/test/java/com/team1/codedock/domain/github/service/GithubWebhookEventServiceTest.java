package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubWebhookEventServiceTest {

    @Mock
    private WorkspaceEventService workspaceEventService;

    @Mock
    private GithubPullRequestRepository githubPullRequestRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GithubWebhookEventService githubWebhookEventService;

    @Test
    @DisplayName("PR 생성 이벤트를 채널과 원본 발생 시각까지 함께 기록한다")
    void onPrCreated() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 23, 9, 30);

        githubWebhookEventService.onPrCreated(
                10L, 5L, "actor", "PR title", 7L, "my-repo", 30L, 234L, occurredAt);

        verify(workspaceEventService).recordPrCreatedIfAbsent(
                10L, 5L, "actor", "PR title", 7L, "my-repo", 30L, 234L, occurredAt);
    }

    @Test
    @DisplayName("Issue 생성 이벤트를 채널과 원본 발생 시각까지 함께 기록한다")
    void onIssueCreated() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 22, 8, 0);

        githubWebhookEventService.onIssueCreated(
                10L, 3L, "actor", "Issue title", 7L, "my-repo", 30L, 42L, occurredAt);

        verify(workspaceEventService).recordIssueCreatedIfAbsent(
                10L, 3L, "actor", "Issue title", 7L, "my-repo", 30L, 42L, occurredAt);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트는 PR 작성자를 targetUserId로 설정한다")
    void onPrReviewUsesPullRequestAuthorAsTargetUser() {
        GithubPullRequest pr = org.mockito.Mockito.mock(GithubPullRequest.class);
        User author = org.mockito.Mockito.mock(User.class);
        when(pr.getAuthor()).thenReturn("review-target");
        when(author.getId()).thenReturn(88L);
        when(githubPullRequestRepository.findById(5L)).thenReturn(Optional.of(pr));
        when(userRepository.findByGithubUsername("review-target")).thenReturn(Optional.of(author));

        githubWebhookEventService.onPrReview(
                10L, 5L, "reviewer", "approved", 7L, "my-repo", 30L, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW,
                "reviewer", 5L, null, 30L, "approved",
                7L, "my-repo", null, 234L, null, 88L);
    }

    @Test
    @DisplayName("PR 리뷰 대상 사용자가 없으면 targetUserId 없이 기록한다")
    void onPrReviewWithoutTargetUser() {
        GithubPullRequest pr = org.mockito.Mockito.mock(GithubPullRequest.class);
        when(pr.getAuthor()).thenReturn("unknown-author");
        when(githubPullRequestRepository.findById(5L)).thenReturn(Optional.of(pr));
        when(userRepository.findByGithubUsername("unknown-author")).thenReturn(Optional.empty());

        githubWebhookEventService.onPrReview(
                10L, 5L, "reviewer", "comment", 7L, "my-repo", 30L, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW,
                "reviewer", 5L, null, 30L, "comment",
                7L, "my-repo", null, 234L, null, null);
    }
}
