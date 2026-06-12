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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Set;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;

    private static final Set<String> ASSIGNABLE_ROLES = Set.of("admin", "editor", "viewer");

    @PersistenceContext
    private EntityManager em;

    @Value("${app.base-url}")
    private String baseUrl;

    public WorkspaceCreateResponse createWorkspace(WorkspaceCreateRequest req, Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (workspaceRepository.countBySlug(req.getSlug()) > 0) {
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
        if (!invitation.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = invitation.getWorkspace();
        if (workspaceMemberRepository.countByWorkspaceAndUser(workspace, user) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        workspaceMemberRepository.save(
                WorkspaceMember.create(workspace, user, invitation.getInvitedAuthority())
        );
        invitation.accept();
    }

    public void rejectInvite(String token, Long currentUserId) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!"pending".equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!invitation.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        invitation.reject();
    }

    public List<InvitationResponse> listInvitations(Long workspaceId, Long currentUserId) {
        WorkspaceMember requester = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(requester.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return invitationRepository.findAllByWorkspace(requester.getWorkspace()).stream()
                .map(InvitationResponse::from)
                .toList();
    }

    public void revokeInvitation(Long workspaceId, Long invitationId, Long currentUserId) {
        WorkspaceMember requester = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(requester.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Invitation invitation = invitationRepository.findByIdAndWorkspace_Id(invitationId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if ("revoked".equals(invitation.getStatus())) {
            return;   // already revoked → idempotent no-op (DELETE is idempotent)
        }
        if (!"pending".equals(invitation.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);   // accepted/rejected invite can't be revoked
        }
        invitation.revoke(requester);
    }

    public void changeMemberRole(Long workspaceId, Long memberId, String newRole, Long currentUserId) {
        if (newRole == null || !ASSIGNABLE_ROLES.contains(newRole)) {   // null-safe: Set.of(...).contains(null) throws NPE
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.changeAuthority(newRole);
    }

    public void removeMember(Long workspaceId, Long memberId, Long currentUserId) {
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.deactivate("removed_by_admin");
    }

    public void leaveWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);   // 403s if not an active member
        if ("owner".equals(membership.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);   // owner must transfer or delete the workspace
        }
        membership.deactivate("left");
    }

    public void deleteWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        if (!membership.getAuthority().equals("owner")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // PR 하위 (PR → GithubRepository/Channel 참조)
        em.createQuery("DELETE FROM PullRequestReviewComment c WHERE c.pullRequest.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM PullRequestReviewRequest r WHERE r.pullRequest.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM PullRequestReview r WHERE r.pullRequest.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM PullRequestChecklistItem i WHERE i.pullRequest.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM GithubPullRequest p WHERE p.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();

        // Issue 하위
        em.createQuery("DELETE FROM IssueAssignee a WHERE a.githubIssue.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM GithubIssue i WHERE i.githubRepository.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();

        // 채널 하위
        em.createQuery("DELETE FROM Bookmark b WHERE b.workspaceMember.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM ChannelReadStatus s WHERE s.channel.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM ThreadReply r WHERE r.thread.channel.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Mention m WHERE m.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Reaction r WHERE r.workspaceMember.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Thread t WHERE t.channel.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();

        // 워크스페이스 직접 참조 테이블
        em.createQuery("DELETE FROM ActivityLog a WHERE a.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Channel c WHERE c.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM GithubRepository g WHERE g.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM ErdTable t WHERE t.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM ErdDocument d WHERE d.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Document d WHERE d.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM ApiSpec a WHERE a.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();

        // WorkspaceMember 하위
        em.createQuery("DELETE FROM WorkspaceMemberPreferences p WHERE p.workspaceMember.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM Invitation i WHERE i.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();
        em.createQuery("DELETE FROM WorkspaceMember m WHERE m.workspace.id = :wid").setParameter("wid", workspaceId).executeUpdate();

        workspaceRepository.deleteById(workspaceId);
    }

    private WorkspaceMember getMembership(Long workspaceId, Long currentUserId) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        WorkspaceMember membership = workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        if (!membership.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return membership;
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