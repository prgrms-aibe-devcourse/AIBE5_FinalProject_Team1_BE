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

import java.util.Optional;

import static org.mockito.Mockito.mock;
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
    @DisplayName("PR 생성 이벤트를 기록한다")
    void onPrCreated() {
        githubWebhookEventService.onPrCreated(10L, 5L, "actor", "PR title", 7L, "my-repo", 11L, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_CREATED, "actor", 5L, null, 11L, "PR title", 7L, "my-repo", null, 234L, null, null);
    }

    @Test
    @DisplayName("이슈 생성 이벤트를 기록한다")
    void onIssueCreated() {
        githubWebhookEventService.onIssueCreated(10L, 3L, "actor", "Issue title", 7L, "my-repo", 22L, 42L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.ISSUE_CREATED, "actor", null, 3L, 22L, "Issue title", 7L, "my-repo", null, null, 42L, null);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트를 기록한다 - PR 작성자 조회 성공 시 targetUserId를 설정한다")
    void onPrReview_작성자_조회_성공() {
        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getAuthor()).thenReturn("octocat");
        User user = mock(User.class);
        when(user.getId()).thenReturn(99L);

        when(githubPullRequestRepository.findById(5L)).thenReturn(Optional.of(pr));
        when(userRepository.findByGithubUsername("octocat")).thenReturn(Optional.of(user));

        githubWebhookEventService.onPrReview(10L, 5L, "actor", "LGTM", 7L, "my-repo", null, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW, "actor", 5L, null, null, "LGTM", 7L, "my-repo", null, 234L, null, 99L);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트를 기록한다 - PR 작성자 조회 실패 시 targetUserId는 null이다")
    void onPrReview_작성자_조회_실패_PR없음() {
        when(githubPullRequestRepository.findById(5L)).thenReturn(Optional.empty());

        githubWebhookEventService.onPrReview(10L, 5L, "actor", "LGTM", 7L, "my-repo", null, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW, "actor", 5L, null, null, "LGTM", 7L, "my-repo", null, 234L, null, null);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트를 기록한다 - PR은 찾았으나 GitHub 유저 조회 실패 시 targetUserId는 null이다")
    void onPrReview_작성자_조회_실패_유저없음() {
        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getAuthor()).thenReturn("octocat");

        when(githubPullRequestRepository.findById(5L)).thenReturn(Optional.of(pr));
        when(userRepository.findByGithubUsername("octocat")).thenReturn(Optional.empty());

        githubWebhookEventService.onPrReview(10L, 5L, "actor", "LGTM", 7L, "my-repo", null, 234L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW, "actor", 5L, null, null, "LGTM", 7L, "my-repo", null, 234L, null, null);
    }
}
