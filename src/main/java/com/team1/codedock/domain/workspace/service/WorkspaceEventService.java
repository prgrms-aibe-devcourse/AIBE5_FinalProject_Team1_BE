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
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        workspaceEventRepository.save(
                WorkspaceEvent.create(workspace, type, actorName, prId, issueId, channelId, content, repositoryId, repositoryName, threadId, prNumber, issueNumber, targetUserId)
        );
        workspace.updateLastActivityAt(LocalDateTime.now());
    }

    // 동시 요청에서는 최종 방어를 DB 제약으로 해야 하므로, 여기서는 일반적인 sync/webhook 재호출 중복만 완화함.
    public void recordPrCreatedIfAbsent(Long workspaceId, Long prId, String actorName, String title,
                                         Long repositoryId, String repositoryName, Long prNumber) {
        if (prId == null || workspaceEventRepository.existsByTypeAndPrId(WorkspaceEvent.EventType.PR_CREATED, prId)) {
            return;
        }

        recordEvent(workspaceId, WorkspaceEvent.EventType.PR_CREATED,
                actorName, prId, null, null, title, repositoryId, repositoryName, null, prNumber, null, null);
    }

    // 동시 요청에서는 최종 방어를 DB 제약으로 해야 하므로, 여기서는 일반적인 sync/webhook 재호출 중복만 완화함.
    public void recordIssueCreatedIfAbsent(Long workspaceId, Long issueId, String actorName, String title,
                                            Long repositoryId, String repositoryName, Long issueNumber) {
        if (issueId == null || workspaceEventRepository.existsByTypeAndIssueId(WorkspaceEvent.EventType.ISSUE_CREATED, issueId)) {
            return;
        }

        recordEvent(workspaceId, WorkspaceEvent.EventType.ISSUE_CREATED,
                actorName, null, issueId, null, title, repositoryId, repositoryName, null, null, issueNumber, null);
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
