package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GithubWebhookEventServiceTest {

    @Mock
    private WorkspaceEventService workspaceEventService;

    @InjectMocks
    private GithubWebhookEventService githubWebhookEventService;

    @Test
    @DisplayName("PR 생성 이벤트를 기록한다")
    void onPrCreated() {
        githubWebhookEventService.onPrCreated(10L, 5L, "actor", "PR title", 7L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_CREATED, "actor", 5L, null, null, "PR title", 7L, null);
    }

    @Test
    @DisplayName("이슈 생성 이벤트를 기록한다")
    void onIssueCreated() {
        githubWebhookEventService.onIssueCreated(10L, 3L, "actor", "Issue title", 7L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.ISSUE_CREATED, "actor", null, 3L, null, "Issue title", 7L, null);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트를 기록한다")
    void onPrReview() {
        githubWebhookEventService.onPrReview(10L, 5L, "actor", "LGTM", 7L);

        verify(workspaceEventService).recordEvent(
                10L, WorkspaceEvent.EventType.PR_REVIEW, "actor", 5L, null, null, "LGTM", 7L, null);
    }
}
