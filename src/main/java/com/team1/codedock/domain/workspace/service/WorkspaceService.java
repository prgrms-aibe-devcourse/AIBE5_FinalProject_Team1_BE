package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.*;
import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.entity.WorkspaceMemberPreferences;
import com.team1.codedock.domain.workspace.repository.InvitationRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberPreferencesRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final WorkspaceMemberPreferencesRepository preferencesRepository;
    private final SimpMessagingTemplate messagingTemplate;

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
                .map(m -> {
                    String presence = preferencesRepository.findByWorkspaceMember(m)
                            .map(WorkspaceMemberPreferences::getPresence)
                            .orElse("active");
                    return WorkspaceMemberResponse.from(m, presence);
                })
                .toList();
    }

    public void updatePresence(Long workspaceId, String presence, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        WorkspaceMemberPreferences prefs = preferencesRepository.findByWorkspaceMember(membership)
                .orElseGet(() -> WorkspaceMemberPreferences.create(membership));
        prefs.updatePresence(presence);
        preferencesRepository.save(prefs);

        java.util.Map<String, Object> event = new java.util.HashMap<>();
        event.put("memberId", membership.getId());
        event.put("userId", currentUserId);
        event.put("username", membership.getUser().getUsername());
        event.put("presence", presence);
        messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId + "/presence", event);
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
            throw new BusinessException     (ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!invitation.getInvitedEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        invitation.reject();
    }

    public List<ReceivedInviteResponse> listReceivedInvites(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return invitationRepository.findAllByInvitedEmailIgnoreCaseAndStatus(user.getEmail(), "pending").stream()
                .filter(inv -> inv.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(inv -> ReceivedInviteResponse.from(inv, workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(inv.getWorkspace())))
                .toList();
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

    public void transferOwnership(Long workspaceId, Long memberId, Long currentUserId) {
        WorkspaceMember requester = getMembership(workspaceId, currentUserId);
        if (!"owner".equals(requester.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        WorkspaceMember target = workspaceMemberRepository.findByIdAndWorkspace_Id(memberId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));
        if (!target.isActive() || target.getId().equals(requester.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        requester.changeAuthority("admin");
        target.changeAuthority("owner");
    }

    public void deleteWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        if (!membership.getAuthority().equals("owner")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        em.flush();
        em.clear();

        String prIds = "(SELECT id FROM github_pull_requests WHERE repository_id IN (SELECT id FROM github_repositories WHERE workspace_id = :wid))";
        String issueIds = "(SELECT id FROM github_issues WHERE repository_id IN (SELECT id FROM github_repositories WHERE workspace_id = :wid))";
        String memberIds = "(SELECT id FROM workspace_members WHERE workspace_id = :wid)";
        String channelIds = "(SELECT id FROM channels WHERE workspace_id = :wid)";
        String threadIds = "(SELECT id FROM threads WHERE channel_id IN " + channelIds + ")";
        String prAnalysisIds = "(SELECT id FROM pull_request_analysis WHERE github_pull_request_id IN " + prIds + ")";

        // PR 하위
        nq("DELETE FROM pull_request_review_comments WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_review_requests WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_reviews WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_checklist_items WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_analysis_findings WHERE pull_request_analysis_id IN " + prAnalysisIds, workspaceId);
        nq("DELETE FROM pull_request_analysis WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_diff_lines WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_files WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM pull_request_labels WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM ai_summaries WHERE github_pull_request_id IN " + prIds, workspaceId);
        nq("DELETE FROM github_pull_requests WHERE repository_id IN (SELECT id FROM github_repositories WHERE workspace_id = :wid)", workspaceId);

        // Issue 하위
        nq("DELETE FROM issue_assignees WHERE github_issue_id IN " + issueIds, workspaceId);
        nq("DELETE FROM issue_labels WHERE github_issue_id IN " + issueIds, workspaceId);
        nq("DELETE FROM ai_summaries WHERE github_issue_id IN " + issueIds, workspaceId);
        nq("DELETE FROM github_issues WHERE repository_id IN (SELECT id FROM github_repositories WHERE workspace_id = :wid)", workspaceId);

        // 채널 하위
        nq("DELETE FROM bookmarks WHERE workspace_member_id IN " + memberIds, workspaceId);
        nq("DELETE FROM channel_read_status WHERE channel_id IN " + channelIds, workspaceId);
        nq("DELETE FROM thread_replies WHERE thread_id IN " + threadIds, workspaceId);
        nq("DELETE FROM thread_attachments WHERE thread_id IN " + threadIds, workspaceId);
        nq("DELETE FROM mentions WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM reactions WHERE workspace_member_id IN " + memberIds, workspaceId);
        nq("DELETE FROM threads WHERE channel_id IN " + channelIds, workspaceId);

        // 워크스페이스 직접 참조 테이블
        nq("DELETE FROM activity_logs WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM channels WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM github_repositories WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM erd_tables WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM erd_documents WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM documents WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM api_specs WHERE workspace_id = :wid", workspaceId);

        // WorkspaceMember 하위
        nq("DELETE FROM workspace_member_preferences WHERE workspace_member_id IN " + memberIds, workspaceId);
        nq("DELETE FROM invitations WHERE workspace_id = :wid", workspaceId);
        nq("DELETE FROM workspace_members WHERE workspace_id = :wid", workspaceId);

        nq("DELETE FROM workspaces WHERE id = :wid", workspaceId);
    }

    private void nq(String sql, Long workspaceId) {
        em.createNativeQuery(sql).setParameter("wid", workspaceId).executeUpdate();
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