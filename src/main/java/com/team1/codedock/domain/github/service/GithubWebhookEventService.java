package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class GithubWebhookEventService {

    private final WorkspaceEventService workspaceEventService;

    public void onPrCreated(Long workspaceId, Long prId, String actorName, String title, Long repositoryId, String repositoryName, Long prNumber) {
        workspaceEventService.recordEvent(workspaceId, WorkspaceEvent.EventType.PR_CREATED,
                actorName, prId, null, null, title, repositoryId, repositoryName, null, prNumber, null);
    }

    public void onIssueCreated(Long workspaceId, Long issueId, String actorName, String title, Long repositoryId, String repositoryName, Long issueNumber) {
        workspaceEventService.recordEvent(workspaceId, WorkspaceEvent.EventType.ISSUE_CREATED,
                actorName, null, issueId, null, title, repositoryId, repositoryName, null, null, issueNumber);
    }

    public void onPrReview(Long workspaceId, Long prId, String actorName, String comment, Long repositoryId, String repositoryName, Long prNumber) {
        workspaceEventService.recordEvent(workspaceId, WorkspaceEvent.EventType.PR_REVIEW,
                actorName, prId, null, null, comment, repositoryId, repositoryName, null, prNumber, null);
    }
}
