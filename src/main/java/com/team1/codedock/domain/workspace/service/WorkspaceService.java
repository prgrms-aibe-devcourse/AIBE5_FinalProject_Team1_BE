package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.*;
import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.InvitationRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    @Value("${app.base-url}")
    private String baseUrl;

    public WorkspaceCreateResponse createWorkspace(WorkspaceCreateRequest req, Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (workspaceRepository.existsBySlug(req.getSlug())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Workspace workspace = workspaceRepository.save(
                Workspace.create(user, req.getName(), req.getSlug(), req.getDescription())
        );
        workspaceMemberRepository.save(WorkspaceMember.create(workspace, user, "owner"));
        return WorkspaceCreateResponse.from(workspace);
    }

    public List<WorkspaceResponse> getMyWorkspaces(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return workspaceMemberRepository.findAllByUser(user).stream()
                .filter(WorkspaceMember::isActive)
                .map(membership -> {
                    Workspace workspace = membership.getWorkspace();
                    int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
                    return WorkspaceResponse.from(workspace, membership, count);
                })
                .toList();
    }

    public WorkspaceResponse getWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        Workspace workspace = membership.getWorkspace();
        int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
        return WorkspaceResponse.from(workspace, membership, count);
    }

    public WorkspaceResponse updateWorkspace(Long workspaceId, WorkspaceUpdateRequest req, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(membership.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = membership.getWorkspace();
        String trimmedName = req.getName() != null ? req.getName().trim() : null;
        workspace.update(trimmedName, req.getDescription());
        int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
        return WorkspaceResponse.from(workspace, membership, count);
    }

    public List<WorkspaceMemberResponse> getMembers(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        Workspace workspace = membership.getWorkspace();
        return workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                .filter(WorkspaceMember::isActive)
                .map(WorkspaceMemberResponse::from)
                .toList();
    }

    public InviteResponse createInvite(Long workspaceId, InviteCreateRequest req, Long currentUserId) {
        WorkspaceMember inviterMember = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(inviterMember.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = inviterMember.getWorkspace();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(req.getExpiresInHours());
        Invitation invitation = invitationRepository.save(
                Invitation.create(workspace, inviterMember, req.getEmail(), req.getRole(), token, expiresAt)
        );
        return InviteResponse.from(invitation, baseUrl);
    }

    public void acceptInvite(String token, Long currentUserId) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EXPIRED_TOKEN);
        }
        if (!invitation.getStatus().equals("pending")) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!invitation.getInvitedEmail().equals(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = invitation.getWorkspace();
        if (workspaceMemberRepository.existsByWorkspaceAndUser(workspace, user)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        workspaceMemberRepository.save(
                WorkspaceMember.create(workspace, user, invitation.getInvitedAuthority())
        );
        invitation.accept();
    }

    public void changeMemberRole(Long workspaceId, Long memberId, String newRole, Long currentUserId) {
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.changeAuthority(newRole);
    }

    public void removeMember(Long workspaceId, Long memberId, Long currentUserId) {
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.deactivate("removed_by_admin");
    }

    private WorkspaceMember getMembership(Long workspaceId, Long currentUserId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private WorkspaceMember validateAndGetTarget(Long workspaceId, Long memberId, Long currentUserId) {
        WorkspaceMember requester = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(requester.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        WorkspaceMember target = workspaceMemberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
        if (!target.getWorkspace().getId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
        }
        if (target.getAuthority().equals("owner")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return target;
    }
}