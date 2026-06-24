package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.WorkspaceEventResponse;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventReadStatusRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkspaceEventService {

    private final WorkspaceEventRepository workspaceEventRepository;
    private final WorkspaceEventReadStatusRepository workspaceEventReadStatusRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public void recordEvent(Long workspaceId, WorkspaceEvent.EventType type, String actorName,
                            Long prId, Long issueId, Long channelId, String content,
                            Long repositoryId, String repositoryName, Long threadId, Long prNumber, Long issueNumber,
                            Long targetUserId) {
        recordEvent(workspaceId, type, actorName, prId, issueId, channelId, content,
                repositoryId, repositoryName, threadId, prNumber, issueNumber, targetUserId, null);
    }

    public void recordEvent(Long workspaceId, WorkspaceEvent.EventType type, String actorName,
                            Long prId, Long issueId, Long channelId, String content,
                            Long repositoryId, String repositoryName, Long threadId, Long prNumber, Long issueNumber,
                            Long targetUserId, LocalDateTime occurredAt) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        workspaceEventRepository.save(
                WorkspaceEvent.create(workspace, type, actorName, prId, issueId, channelId, content,
                        repositoryId, repositoryName, threadId, prNumber, issueNumber, targetUserId, occurredAt)
        );
        workspace.updateLastActivityAt(LocalDateTime.now());
    }

    public void recordPrCreatedIfAbsent(Long workspaceId, Long prId, String actorName, String title,
                                         Long repositoryId, String repositoryName, Long channelId, Long prNumber) {
        recordPrCreatedIfAbsent(workspaceId, prId, actorName, title,
                repositoryId, repositoryName, channelId, prNumber, null);
    }

    public void recordPrCreatedIfAbsent(Long workspaceId, Long prId, String actorName, String title,
                                         Long repositoryId, String repositoryName, Long channelId, Long prNumber,
                                         LocalDateTime occurredAt) {
        if (prId == null || workspaceEventRepository.existsByTypeAndPrId(WorkspaceEvent.EventType.PR_CREATED, prId)) {
            return;
        }

        // sync/webhook 동시 호출 중복은 best-effort로 줄이고, 최종 방어는 DB 정책에서 처리해야 함.
        recordEvent(workspaceId, WorkspaceEvent.EventType.PR_CREATED,
                actorName, prId, null, channelId, title, repositoryId, repositoryName, null, prNumber, null, null, occurredAt);
    }

    public void recordIssueCreatedIfAbsent(Long workspaceId, Long issueId, String actorName, String title,
                                            Long repositoryId, String repositoryName, Long channelId, Long issueNumber) {
        recordIssueCreatedIfAbsent(workspaceId, issueId, actorName, title,
                repositoryId, repositoryName, channelId, issueNumber, null);
    }

    public void recordIssueCreatedIfAbsent(Long workspaceId, Long issueId, String actorName, String title,
                                            Long repositoryId, String repositoryName, Long channelId, Long issueNumber,
                                            LocalDateTime occurredAt) {
        if (issueId == null || workspaceEventRepository.existsByTypeAndIssueId(WorkspaceEvent.EventType.ISSUE_CREATED, issueId)) {
            return;
        }

        // sync/webhook 동시 호출 중복은 best-effort로 줄이고, 최종 방어는 DB 정책에서 처리해야 함.
        recordEvent(workspaceId, WorkspaceEvent.EventType.ISSUE_CREATED,
                actorName, null, issueId, channelId, title, repositoryId, repositoryName, null, null, issueNumber, null, occurredAt);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceEventResponse> getEventsForUser(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> workspaceIds = workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(userId).stream()
                .map(m -> m.getWorkspace().getId())
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        List<WorkspaceEvent.EventType> broadcastTypes = List.of(
                WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED);
        List<WorkspaceEvent.EventType> targetedTypes = List.of(
                WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION);
        List<WorkspaceEvent> events = workspaceEventRepository
                .findDashboardEvents(workspaceIds, userId, broadcastTypes, targetedTypes, PageRequest.of(0, 50));

        Set<Long> readEventIds = events.isEmpty() ? Set.of()
                : workspaceEventReadStatusRepository.findReadEventIdsByUserIdAndEventIds(
                        userId, events.stream().map(WorkspaceEvent::getId).toList());

        return events.stream()
                .map(e -> WorkspaceEventResponse.from(e, readEventIds.contains(e.getId())))
                .toList();
    }
}
