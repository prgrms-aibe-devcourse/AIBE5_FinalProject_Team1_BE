package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final WorkspaceMemberPreferencesRepository preferencesRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PresenceRegistry presenceRegistry;

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
        // 새 워크스페이스가 채널 0개 상태면 채팅 WebSocket 연결 조건을 못 탐. 기본 채널을 같이 생성함.
        Channel defaultChannel = channelRepository.save(Channel.createGeneral(workspace));
        return WorkspaceCreateResponse.from(workspace, defaultChannel.getId());
    }

    public List<WorkspaceResponse> getMyWorkspaces(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return workspaceMemberRepository.findAllByUser(user).stream()
                .filter(WorkspaceMember::isActive)
                .map(membership -> {
                    Workspace workspace = membership.getWorkspace();
                    int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
                    // 실시간 접속 인원 중 "본인을 제외한" 다른 멤버 수만 계산한다.
                    // 본인은 FE가 자신의 실시간 상태(userPresence)로 더한다 → REST/WS 등록 타이밍이나
                    // 저장된(stale) presence와 무관하게 본인 카운트가 정확해짐.
                    int othersOnline = (int) workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                            .filter(WorkspaceMember::isActive)
                            .filter(member -> !member.getUser().getId().equals(currentUserId))
                            .filter(member -> presenceRegistry.isOnline(member.getUser().getId()))
                            .filter(member -> !"offline".equals(preferencesRepository.findByWorkspaceMember(member)
                                    .map(WorkspaceMemberPreferences::getPresence)
                                    .orElse("active")))
                            .count();
                    return WorkspaceResponse.from(workspace, membership, count, othersOnline);
                })
                .toList();
    }

    public WorkspaceResponse getWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        Workspace workspace = membership.getWorkspace();
        int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
        return WorkspaceResponse.fromDetail(workspace, membership, count);
    }

    public WorkspaceResponse updateWorkspace(Long workspaceId, WorkspaceUpdateRequest req, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(membership.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = membership.getWorkspace();
        String trimmedName = req.getName() != null ? req.getName().trim() : null;
        workspace.update(trimmedName, req.getDescription(), req.getLogoUrl());
        publishMemberEvent(workspaceId, "WORKSPACE_UPDATED");
        int count = workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace);
        return WorkspaceResponse.fromDetail(workspace, membership, count);
    }

    public List<WorkspaceMemberResponse> getMembers(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);
        Workspace workspace = membership.getWorkspace();
        return workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                .filter(WorkspaceMember::isActive)
                .map(m -> {
                    // 접속 세션이 없는 멤버는 고른 상태와 무관하게 offline로 내려줌(목록 진입 시 깜빡임 방지).
                    String chosen = preferencesRepository.findByWorkspaceMember(m)
                            .map(WorkspaceMemberPreferences::getPresence)
                            .orElse("active");
                    String presence = presenceRegistry.isOnline(m.getUser().getId()) ? chosen : "offline";
                    return WorkspaceMemberResponse.from(m, presence);
                })
                .toList();
    }

    public void updatePresence(Long workspaceId, String presence, Long currentUserId) {
        // presence는 사용자의 전역 상태로 취급한다. 요청 워크스페이스 멤버인지 검증한 뒤,
        // 그 사용자의 모든 활성 멤버십에 동일 상태를 적용하고 각 워크스페이스 토픽으로 브로드캐스트한다.
        // (한 워크스페이스에서 offline으로 바꾸면 다른 워크스페이스에서도 즉시 offline로 반영됨)
        WorkspaceMember requesting = getMembership(workspaceId, currentUserId);
        User user = requesting.getUser();
        for (WorkspaceMember member : workspaceMemberRepository.findAllByUser(user)) {
            if (!member.isActive()) {
                continue;
            }
            WorkspaceMemberPreferences prefs = preferencesRepository.findByWorkspaceMember(member)
                    .orElseGet(() -> WorkspaceMemberPreferences.create(member));
            prefs.updatePresence(presence);
            preferencesRepository.save(prefs);
            publishPresenceEvent(member.getWorkspace().getId(), currentUserId, member, presence);
        }
    }

    // 새 구독자에게만 현재 워크스페이스 멤버들의 presence 스냅샷을 전송함.
    // 접속 세션이 없는 멤버는 고른 상태와 무관하게 offline로 내려줌(실시간 연결 기준).
    @Transactional(readOnly = true)
    public void sendPresenceSnapshot(Long workspaceId, String recipientName, Set<Long> onlineUserIds) {
        if (recipientName == null) {
            return;
        }
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            return;
        }
        List<Map<String, Object>> snapshot = workspaceMemberRepository.findAllByWorkspace(workspace).stream()
                .filter(WorkspaceMember::isActive)
                .map(member -> {
                    String chosen = preferencesRepository.findByWorkspaceMember(member)
                            .map(WorkspaceMemberPreferences::getPresence)
                            .orElse("active");
                    boolean online = onlineUserIds.contains(member.getUser().getId());
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("memberId", member.getId());
                    entry.put("presence", online ? chosen : "offline");
                    return entry;
                })
                .toList();
        messagingTemplate.convertAndSendToUser(recipientName, "/queue/presence", snapshot);
    }

    // 사이트 접속(첫 세션)/완전 종료(마지막 세션) 시, 그 사용자가 속한 모든 활성 워크스페이스의
    // presence 토픽으로 상태를 브로드캐스트함. ("워크스페이스를 보고 있지 않아도 사이트 접속=온라인" 모델)
    @Transactional(readOnly = true)
    public void broadcastUserPresenceToAllWorkspaces(Long userId, boolean online) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        for (WorkspaceMember member : workspaceMemberRepository.findAllByUser(user)) {
            if (!member.isActive()) {
                continue;
            }
            Long workspaceId = member.getWorkspace().getId();
            String presence = online
                    ? preferencesRepository.findByWorkspaceMember(member)
                            .map(WorkspaceMemberPreferences::getPresence)
                            .orElse("active")
                    : "offline";
            publishPresenceEvent(workspaceId, userId, member, presence);
        }
    }

    private void publishPresenceEvent(Long workspaceId, Long userId, WorkspaceMember member, String presence) {
        Map<String, Object> event = new HashMap<>();
        event.put("workspaceId", workspaceId);
        event.put("memberId", member.getId());
        event.put("workspaceMemberId", member.getId());
        event.put("userId", userId);
        event.put("username", member.getUser().getUsername());
        event.put("presence", presence);
        messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId + "/presence", event);
    }

    public InviteResponse createInvite(Long workspaceId, InviteCreateRequest req, Long currentUserId) {
        WorkspaceMember inviterMember = getMembership(workspaceId, currentUserId);
        if (!List.of("owner", "admin").contains(inviterMember.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String invitedEmail = req.getEmail().trim().toLowerCase(Locale.ROOT);
        User invitedOwner = resolveOwner(invitedEmail);
        if (invitedOwner != null
                && workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, invitedOwner.getId()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if (invitationRepository.existsByWorkspace_IdAndInvitedEmailIgnoreCaseAndStatus(workspaceId, invitedEmail, "pending")) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        Workspace workspace = inviterMember.getWorkspace();
        String token = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(req.getExpiresInHours());
        Invitation invitation = invitationRepository.save(
                Invitation.create(workspace, inviterMember, invitedEmail, req.getRole(), req.getPosition(), token, expiresAt)
        );
        if (invitedOwner != null) {
            publishInviteEvent(invitedOwner.getEmail(), "RECEIVED", workspaceId);
        }
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
        User owner = resolveOwner(invitation.getInvitedEmail());
        if (owner == null || !owner.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Workspace workspace = invitation.getWorkspace();
        WorkspaceMember existing = workspaceMemberRepository.findByWorkspaceAndUser(workspace, user).orElse(null);
        if (existing != null) {
            if (existing.isActive()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            existing.reactivate(invitation.getInvitedAuthority());
            existing.assignPosition(invitation.getInvitedPosition());
        } else {
            WorkspaceMember member = WorkspaceMember.create(workspace, user, invitation.getInvitedAuthority());
            member.assignPosition(invitation.getInvitedPosition());
            workspaceMemberRepository.save(member);
        }
        invitation.accept();
        publishInviteEvent(invitation.getInviterMember().getUser().getEmail(), "ACCEPTED", invitation.getWorkspace().getId());
        publishMemberEvent(invitation.getWorkspace().getId(), "JOINED");
    }

    public void rejectInvite(String token, Long currentUserId) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!"pending".equals(invitation.getStatus())) {
            throw new BusinessException     (ErrorCode.INVALID_TOKEN);
        }
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        User owner = resolveOwner(invitation.getInvitedEmail());
        if (owner == null || !owner.getId().equals(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        invitation.reject();
        publishInviteEvent(invitation.getInviterMember().getUser().getEmail(), "REJECTED", invitation.getWorkspace().getId());
    }

    private User resolveOwner(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCaseOrderByIdAsc(email).stream().findFirst()
                .orElseGet(() -> userRepository.findByGithubEmailIgnoreCaseOrderByIdAsc(email)
                        .stream().findFirst().orElse(null));
    }

    public List<ReceivedInviteResponse> listReceivedInvites(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Set<String> candidateEmails = new HashSet<>();
        if (user.getEmail() != null) {
            candidateEmails.add(user.getEmail().toLowerCase(Locale.ROOT));
        }
        if (user.getGithubEmail() != null) {
            candidateEmails.add(user.getGithubEmail().toLowerCase(Locale.ROOT));
        }
        if (candidateEmails.isEmpty()) {
            return List.of();
        }
        return invitationRepository.findAllByStatusAndLoweredInvitedEmailIn("pending", candidateEmails).stream()
                .filter(inv -> inv.getExpiresAt().isAfter(LocalDateTime.now()))
                .filter(inv -> {
                    User owner = resolveOwner(inv.getInvitedEmail());
                    return owner != null && owner.getId().equals(user.getId());
                })
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

        User revokedInvitee = resolveOwner(invitation.getInvitedEmail());
        if (revokedInvitee != null) {
            publishInviteEvent(revokedInvitee.getEmail(), "REVOKED", workspaceId);
        }
    }

    public void changeMemberRole(Long workspaceId, Long memberId, String newRole, Long currentUserId) {
        if (newRole == null || !ASSIGNABLE_ROLES.contains(newRole)) {   // null-safe: Set.of(...).contains(null) throws NPE
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.changeAuthority(newRole);
        publishMemberEvent(workspaceId, "ROLE_CHANGED");
    }

    public void removeMember(Long workspaceId, Long memberId, Long currentUserId) {
        WorkspaceMember target = validateAndGetTarget(workspaceId, memberId, currentUserId);
        target.deactivate("removed_by_admin");
        publishMemberEvent(workspaceId, "REMOVED");
        publishMemberRemovedToUser(target.getUser().getEmail(), workspaceId);
    }

    public void leaveWorkspace(Long workspaceId, Long currentUserId) {
        WorkspaceMember membership = getMembership(workspaceId, currentUserId);   // 403s if not an active member
        if ("owner".equals(membership.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);   // owner는 워크스페이스를 위임하거나 삭제해야 함
        }
        membership.deactivate("left");
        publishMemberEvent(workspaceId, "LEFT");
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
        requester.getWorkspace().changeOwner(target.getUser());
        publishMemberEvent(workspaceId, "OWNERSHIP_TRANSFERRED");
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
        nq("DELETE FROM workspace_events WHERE workspace_id = :wid", workspaceId);
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

    private void publishInviteEvent(String email, String action, Long workspaceId) {
        if (email == null || email.isBlank()) {
            return;
        }
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "INVITE_EVENT");
        payload.put("action", action);
        payload.put("workspaceId", workspaceId);
        eventPublisher.publishEvent(new InviteNotificationEvent(email, payload));
    }

    private void publishMemberEvent(Long workspaceId, String action) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "MEMBER_EVENT");
        payload.put("action", action);
        payload.put("workspaceId", workspaceId);
        eventPublisher.publishEvent(new WorkspaceMemberEvent(workspaceId, payload));
    }

    private void publishMemberRemovedToUser(String email, Long workspaceId) {
        if (email == null || email.isBlank()) {
            return;
        }
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("type", "MEMBER_EVENT");
        payload.put("action", "REMOVED");
        payload.put("workspaceId", workspaceId);
        eventPublisher.publishEvent(new InviteNotificationEvent(email, payload));
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
        // 비활성 멤버는 권한을 바꿔도 접근 권한이 살아나지 않음. 성공처럼 보이지 않게 차단함.
        if (!target.isActive()) {
            throw new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);
        }
        if (target.getAuthority().equals("owner")) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return target;
    }
}
