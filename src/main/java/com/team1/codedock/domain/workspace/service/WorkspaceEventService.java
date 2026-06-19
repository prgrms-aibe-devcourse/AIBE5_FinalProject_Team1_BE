package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.WorkspaceEventResponse;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkspaceEventService {

    private final WorkspaceEventRepository workspaceEventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public void recordEvent(Long workspaceId, WorkspaceEvent.EventType type, String actorName,
                            Long prId, Long issueId, Long channelId, String content,
                            Long repositoryId, Long threadId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        workspaceEventRepository.save(
                WorkspaceEvent.create(workspace, type, actorName, prId, issueId, channelId, content, repositoryId, threadId)
        );
        workspace.updateLastActivityAt(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<WorkspaceEventResponse> getEventsForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        List<Long> workspaceIds = workspaceMemberRepository.findAllByUser(user).stream()
                .filter(WorkspaceMember::isActive)
                .map(m -> m.getWorkspace().getId())
                .toList();
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceEventRepository.findAllByWorkspace_IdInOrderByCreatedAtDesc(workspaceIds).stream()
                .map(WorkspaceEventResponse::from)
                .toList();
    }
}
