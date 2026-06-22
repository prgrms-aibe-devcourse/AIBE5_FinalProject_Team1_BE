package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.WorkspaceCreateRequest;
import com.team1.codedock.domain.workspace.dto.WorkspaceMemberResponse;
import com.team1.codedock.domain.workspace.dto.WorkspaceResponse;
import com.team1.codedock.domain.workspace.dto.InviteCreateRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private WorkspaceMemberPreferencesRepository preferencesRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PresenceRegistry presenceRegistry;

    @Mock
    private WorkspaceLogoStorageService workspaceLogoStorageService;

    @InjectMocks
    private WorkspaceService workspaceService;

    @Test
    @DisplayName("Workspace creation also creates default general channel")
    void createWorkspaceWithDefaultChannel() {
        User owner = user(100L, "owner@test.com");
        WorkspaceCreateRequest request = new WorkspaceCreateRequest();
        request.setName("Team");
        request.setSlug("team");
        request.setDescription("description");

        when(userRepository.findById(100L)).thenReturn(Optional.of(owner));
        when(workspaceRepository.countBySlug("team")).thenReturn(0L);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> {
            Workspace workspace = invocation.getArgument(0);
            ReflectionTestUtils.setField(workspace, "id", 10L);
            return workspace;
        });
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            ReflectionTestUtils.setField(channel, "id", 99L);
            return channel;
        });

        var response = workspaceService.createWorkspace(request, 100L);

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        Channel defaultChannel = channelCaptor.getValue();

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getDefaultChannelId()).isEqualTo(99L);
        assertThat(defaultChannel.getWorkspace().getId()).isEqualTo(10L);
        assertThat(defaultChannel.getName()).isEqualTo(Channel.DEFAULT_GENERAL_NAME);
        assertThat(defaultChannel.getChannelType()).isEqualTo(Channel.TYPE_GENERAL);
        assertThat(defaultChannel.isDeletable()).isFalse();
    }

    @Test
    @DisplayName("워크스페이스 관리자는 로고 파일을 업로드하고 logoUrl을 갱신할 수 있다")
    void updateWorkspaceLogo() {
        Workspace workspace = workspace(10L);
        User adminUser = user(100L, "admin@test.com");
        WorkspaceMember adminMember = workspaceMember(1L, workspace, adminUser, "admin");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(adminUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, adminUser)).thenReturn(Optional.of(adminMember));
        when(workspaceLogoStorageService.storeWorkspaceLogo(10L, file))
                .thenReturn("data:image/png;base64,AQID");
        when(workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(workspace)).thenReturn(3);

        WorkspaceResponse response = workspaceService.updateWorkspaceLogo(10L, file, 100L);

        assertThat(response.getLogoUrl()).isEqualTo("data:image/png;base64,AQID");
        assertThat(workspace.getLogoUrl()).isEqualTo("data:image/png;base64,AQID");
        assertThat(response.getMemberCount()).isEqualTo(3);
        verify(workspaceLogoStorageService).storeWorkspaceLogo(10L, file);
        verify(eventPublisher).publishEvent(any(WorkspaceMemberEvent.class));
    }

    @Test
    @DisplayName("워크스페이스 로고 업로드는 owner/admin이 아니면 거부한다")
    void updateWorkspaceLogoByViewer() {
        Workspace workspace = workspace(10L);
        User viewerUser = user(100L, "viewer@test.com");
        WorkspaceMember viewerMember = workspaceMember(1L, workspace, viewerUser, "viewer");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(viewerUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, viewerUser)).thenReturn(Optional.of(viewerMember));

        assertThatThrownBy(() -> workspaceService.updateWorkspaceLogo(10L, file, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(workspaceLogoStorageService);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("워크스페이스 로고 저장에 실패하면 logoUrl과 이벤트를 변경하지 않는다")
    void updateWorkspaceLogoWithStorageFailure() {
        Workspace workspace = workspace(10L);
        User adminUser = user(100L, "admin@test.com");
        WorkspaceMember adminMember = workspaceMember(1L, workspace, adminUser, "admin");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "logo.png",
                "image/png",
                new byte[]{1, 2, 3}
        );

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(adminUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, adminUser)).thenReturn(Optional.of(adminMember));
        when(workspaceLogoStorageService.storeWorkspaceLogo(10L, file))
                .thenThrow(new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않는 로고 파일입니다."));

        assertThatThrownBy(() -> workspaceService.updateWorkspaceLogo(10L, file, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThat(workspace.getLogoUrl()).isNull();
        verify(eventPublisher, never()).publishEvent(any());
        verify(workspaceMemberRepository, never()).countByWorkspaceAndIsActiveTrue(workspace);
    }

    @Test
    @DisplayName("Active member role can be changed by workspace admin")
    void changeMemberRole() {
        Workspace workspace = workspace(10L);
        User adminUser = user(100L, "admin@test.com");
        WorkspaceMember adminMember = workspaceMember(1L, workspace, adminUser, "admin");
        User targetUser = user(200L, "member@test.com");
        WorkspaceMember targetMember = workspaceMember(2L, workspace, targetUser, "viewer");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(adminUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, adminUser))
                .thenReturn(Optional.of(adminMember));
        when(workspaceMemberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

        workspaceService.changeMemberRole(10L, 2L, "editor", 100L);

        assertThat(targetMember.getAuthority()).isEqualTo("editor");
    }

    @Test
    @DisplayName("Inactive member role change is rejected")
    void changeInactiveMemberRole() {
        Workspace workspace = workspace(10L);
        User adminUser = user(100L, "admin@test.com");
        WorkspaceMember adminMember = workspaceMember(1L, workspace, adminUser, "admin");
        User targetUser = user(200L, "member@test.com");
        WorkspaceMember targetMember = workspaceMember(2L, workspace, targetUser, "viewer");
        targetMember.deactivate("left");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(adminUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, adminUser))
                .thenReturn(Optional.of(adminMember));
        when(workspaceMemberRepository.findById(2L)).thenReturn(Optional.of(targetMember));

        assertThatThrownBy(() -> workspaceService.changeMemberRole(10L, 2L, "editor", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND);

        assertThat(targetMember.getAuthority()).isEqualTo("viewer");
        verify(workspaceMemberRepository, never()).save(targetMember);
    }

    @Test
    @DisplayName("기존 presence 설정이 있으면 값을 갱신하고 워크스페이스 presence 토픽으로 전파한다")
    void updatePresenceWithExistingPreferencesBroadcastsEvent() {
        Workspace workspace = workspace(10L);
        User user = user(100L, "member@test.com");
        WorkspaceMember member = workspaceMember(5L, workspace, user, "editor");
        WorkspaceMemberPreferences preferences = WorkspaceMemberPreferences.create(member);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)).thenReturn(Optional.of(member));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(member));
        when(preferencesRepository.findByWorkspaceMember(member)).thenReturn(Optional.of(preferences));

        workspaceService.updatePresence(10L, "busy", 100L);

        assertThat(preferences.getPresence()).isEqualTo("busy");
        verify(preferencesRepository).save(preferences);
        assertPresenceBroadcast("/topic/workspaces/10/presence", 10L, member, user, "busy");
    }

    @Test
    @DisplayName("presence 설정이 없으면 새로 생성한 뒤 워크스페이스 presence 토픽으로 전파한다")
    void updatePresenceWithoutPreferencesCreatesPreferencesAndBroadcastsEvent() {
        Workspace workspace = workspace(10L);
        User user = user(100L, "member@test.com");
        WorkspaceMember member = workspaceMember(5L, workspace, user, "viewer");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)).thenReturn(Optional.of(member));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(member));
        when(preferencesRepository.findByWorkspaceMember(member)).thenReturn(Optional.empty());

        workspaceService.updatePresence(10L, "away", 100L);

        ArgumentCaptor<WorkspaceMemberPreferences> preferencesCaptor =
                ArgumentCaptor.forClass(WorkspaceMemberPreferences.class);
        verify(preferencesRepository).save(preferencesCaptor.capture());
        WorkspaceMemberPreferences savedPreferences = preferencesCaptor.getValue();

        assertThat(savedPreferences.getWorkspaceMember()).isSameAs(member);
        assertThat(savedPreferences.getPresence()).isEqualTo("away");
        assertPresenceBroadcast("/topic/workspaces/10/presence", 10L, member, user, "away");
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스의 presence 변경은 저장과 전파를 하지 않는다")
    void updatePresenceWithMissingWorkspaceDoesNotSaveOrBroadcast() {
        when(workspaceRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.updatePresence(404L, "busy", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);

        verifyNoInteractions(userRepository, workspaceMemberRepository, preferencesRepository, messagingTemplate);
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 presence 변경은 저장과 전파를 하지 않는다")
    void updatePresenceWithMissingUserDoesNotSaveOrBroadcast() {
        Workspace workspace = workspace(10L);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.updatePresence(10L, "busy", 404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(workspaceMemberRepository, preferencesRepository, messagingTemplate);
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아닌 사용자의 presence 변경은 저장과 전파를 하지 않는다")
    void updatePresenceWithoutMembershipDoesNotSaveOrBroadcast() {
        Workspace workspace = workspace(10L);
        User user = user(100L, "member@test.com");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.updatePresence(10L, "busy", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(preferencesRepository, messagingTemplate);
    }

    @Test
    @DisplayName("비활성 워크스페이스 멤버의 presence 변경은 저장과 전파를 하지 않는다")
    void updatePresenceWithInactiveMembershipDoesNotSaveOrBroadcast() {
        Workspace workspace = workspace(10L);
        User user = user(100L, "member@test.com");
        WorkspaceMember member = workspaceMember(5L, workspace, user, "viewer");
        member.deactivate("left");

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, user)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> workspaceService.updatePresence(10L, "busy", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(preferencesRepository, messagingTemplate);
    }

    @Test
    @DisplayName("초대 수락: invitedEmail이 사용자의 기본 email과 일치하면 수락한다")
    void acceptInvite_matchByEmail_success() {
        User user = user(1L, "a@x.com", null);
        Invitation invitation = pendingInvitation("a@x.com");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("a@x.com")).thenReturn(List.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(invitation.getWorkspace(), user)).thenReturn(Optional.empty());

        workspaceService.acceptInvite("tok", 1L);

        assertThat(invitation.getStatus()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("초대 수락: 기본 email과 불일치해도 githubEmail이 일치하면 수락한다")
    void acceptInvite_matchByGithubEmail_success() {
        User user = user(1L, "a@x.com", "g@x.com");
        Invitation invitation = pendingInvitation("g@x.com");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("g@x.com")).thenReturn(List.of());
        when(userRepository.findByGithubEmailIgnoreCaseOrderByIdAsc("g@x.com")).thenReturn(List.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(invitation.getWorkspace(), user)).thenReturn(Optional.empty());

        workspaceService.acceptInvite("tok", 1L);

        assertThat(invitation.getStatus()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("초대 수락: 기본 email 소유 계정이 우선이므로 githubEmail만 일치하는 다른 사용자는 거부한다")
    void acceptInvite_emailOwnerWinsOverGithubMatch_forbidden() {
        User emailOwner = user(1L, "g@x.com", null);
        User current = user(2L, "b@x.com", "g@x.com");
        Invitation invitation = pendingInvitation("g@x.com");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(2L)).thenReturn(Optional.of(current));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("g@x.com")).thenReturn(List.of(emailOwner));

        assertThatThrownBy(() -> workspaceService.acceptInvite("tok", 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(invitation.getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("초대 수락: 매칭되는 계정이 없으면 거부한다")
    void acceptInvite_noOwner_forbidden() {
        User current = user(1L, "a@x.com", null);
        Invitation invitation = pendingInvitation("none@x.com");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(current));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("none@x.com")).thenReturn(List.of());
        when(userRepository.findByGithubEmailIgnoreCaseOrderByIdAsc("none@x.com")).thenReturn(List.of());

        assertThatThrownBy(() -> workspaceService.acceptInvite("tok", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("초대 수락: githubEmail 다중 일치 시 가장 오래된 계정이 소유자이면 다른 계정은 거부한다")
    void acceptInvite_githubEmailTiebreakOldestWins_forbidden() {
        User oldest = user(1L, "a@x.com", "g@x.com");
        User current = user(2L, "b@x.com", "g@x.com");
        Invitation invitation = pendingInvitation("g@x.com");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(2L)).thenReturn(Optional.of(current));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("g@x.com")).thenReturn(List.of());
        when(userRepository.findByGithubEmailIgnoreCaseOrderByIdAsc("g@x.com")).thenReturn(List.of(oldest, current));

        assertThatThrownBy(() -> workspaceService.acceptInvite("tok", 2L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("초대 생성: 요청의 직무(position)가 초대에 저장된다")
    void createInvite_persistsInvitedPosition() {
        Workspace workspace = workspace(10L);
        User adminUser = user(100L, "admin@test.com");
        WorkspaceMember adminMember = workspaceMember(1L, workspace, adminUser, "admin");
        InviteCreateRequest request = new InviteCreateRequest();
        request.setEmail("invitee@test.com");
        request.setRole("viewer");
        request.setPosition("Backend Developer");
        request.setExpiresInHours(168);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(100L)).thenReturn(Optional.of(adminUser));
        when(workspaceMemberRepository.findByWorkspaceAndUser(workspace, adminUser))
                .thenReturn(Optional.of(adminMember));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("invitee@test.com")).thenReturn(List.of());
        when(userRepository.findByGithubEmailIgnoreCaseOrderByIdAsc("invitee@test.com")).thenReturn(List.of());
        when(invitationRepository.existsByWorkspace_IdAndInvitedEmailIgnoreCaseAndStatus(10L, "invitee@test.com", "pending"))
                .thenReturn(false);
        when(invitationRepository.save(any(Invitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        workspaceService.createInvite(10L, request, 100L);

        ArgumentCaptor<Invitation> captor = ArgumentCaptor.forClass(Invitation.class);
        verify(invitationRepository).save(captor.capture());
        assertThat(captor.getValue().getInvitedPosition()).isEqualTo("Backend Developer");
        assertThat(captor.getValue().getInvitedAuthority()).isEqualTo("viewer");
    }

    @Test
    @DisplayName("초대 수락: 신규 멤버에게 초대의 직무(position)가 배정된다")
    void acceptInvite_assignsPositionToNewMember() {
        User user = user(1L, "a@x.com", null);
        Invitation invitation = pendingInvitation("a@x.com", "Tech Lead");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("a@x.com")).thenReturn(List.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(invitation.getWorkspace(), user)).thenReturn(Optional.empty());

        workspaceService.acceptInvite("tok", 1L);

        ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo("Tech Lead");
        assertThat(invitation.getStatus()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("초대 수락: 재가입(재활성화) 멤버에게도 직무(position)가 배정된다")
    void acceptInvite_assignsPositionToReactivatedMember() {
        User user = user(1L, "a@x.com", null);
        Invitation invitation = pendingInvitation("a@x.com", "QA Engineer");
        WorkspaceMember existing = WorkspaceMember.create(invitation.getWorkspace(), user, "viewer");
        existing.deactivate("left");
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("a@x.com")).thenReturn(List.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(invitation.getWorkspace(), user)).thenReturn(Optional.of(existing));

        workspaceService.acceptInvite("tok", 1L);

        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getPosition()).isEqualTo("QA Engineer");
        assertThat(invitation.getStatus()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("초대 수락: 직무(position)가 없으면 멤버 직무는 null로 정상 처리된다")
    void acceptInvite_nullPositionIsAllowed() {
        User user = user(1L, "a@x.com", null);
        Invitation invitation = pendingInvitation("a@x.com", null);
        when(invitationRepository.findByToken("tok")).thenReturn(Optional.of(invitation));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByEmailIgnoreCaseOrderByIdAsc("a@x.com")).thenReturn(List.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(invitation.getWorkspace(), user)).thenReturn(Optional.empty());

        workspaceService.acceptInvite("tok", 1L);

        ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(workspaceMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isNull();
        assertThat(invitation.getStatus()).isEqualTo("accepted");
    }

    @Test
    @DisplayName("sendPresenceSnapshot: 접속 세션 없는 멤버는 offline로, 개인 큐로 스냅샷을 보낸다")
    @SuppressWarnings("unchecked")
    void sendPresenceSnapshot() {
        Workspace workspace = workspace(1L);
        User a = user(10L, "a@test.com");
        User b = user(20L, "b@test.com");
        WorkspaceMember memberA = workspaceMember(100L, workspace, a, "editor");
        WorkspaceMember memberB = workspaceMember(200L, workspace, b, "viewer");
        ReflectionTestUtils.setField(memberA, "isActive", true);
        ReflectionTestUtils.setField(memberB, "isActive", true);
        WorkspaceMemberPreferences prefsA = WorkspaceMemberPreferences.create(memberA);
        prefsA.updatePresence("away");
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findAllByWorkspace(workspace)).thenReturn(List.of(memberA, memberB));
        when(preferencesRepository.findByWorkspaceMember(memberA)).thenReturn(Optional.of(prefsA));
        when(preferencesRepository.findByWorkspaceMember(memberB)).thenReturn(Optional.empty());

        // A만 접속(online), B는 세션 없음(offline로 내려가야 함)
        workspaceService.sendPresenceSnapshot(1L, "alice", java.util.Set.of(10L));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                org.mockito.ArgumentMatchers.eq("alice"),
                org.mockito.ArgumentMatchers.eq("/queue/presence"),
                payloadCaptor.capture()
        );
        List<Map<String, Object>> snapshot = (List<Map<String, Object>>) payloadCaptor.getValue();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0)).containsEntry("memberId", 100L).containsEntry("presence", "away");
        assertThat(snapshot.get(1)).containsEntry("memberId", 200L).containsEntry("presence", "offline");
    }

    @Test
    @DisplayName("sendPresenceSnapshot: 수신자 이름이 없으면 보내지 않는다")
    void sendPresenceSnapshotWithoutRecipient() {
        workspaceService.sendPresenceSnapshot(1L, null, java.util.Set.of(10L));
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("broadcastUserPresenceToAllWorkspaces(online): 사용자의 모든 활성 워크스페이스에 고른 상태를 보낸다")
    @SuppressWarnings("unchecked")
    void broadcastUserPresenceOnline() {
        User user = user(10L, "alice@test.com");
        Workspace w1 = workspace(1L);
        Workspace w2 = workspace(2L);
        WorkspaceMember m1 = workspaceMember(100L, w1, user, "owner");
        WorkspaceMember m2 = workspaceMember(200L, w2, user, "editor");
        ReflectionTestUtils.setField(m1, "isActive", true);
        ReflectionTestUtils.setField(m2, "isActive", true);
        WorkspaceMemberPreferences prefs1 = WorkspaceMemberPreferences.create(m1);
        prefs1.updatePresence("busy");
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(m1, m2));
        when(preferencesRepository.findByWorkspaceMember(m1)).thenReturn(Optional.of(prefs1));
        when(preferencesRepository.findByWorkspaceMember(m2)).thenReturn(Optional.empty());

        workspaceService.broadcastUserPresenceToAllWorkspaces(10L, true);

        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(destCaptor.capture(), payloadCaptor.capture());

        assertThat(destCaptor.getAllValues())
                .containsExactly("/topic/workspaces/1/presence", "/topic/workspaces/2/presence");
        Map<String, Object> p1 = (Map<String, Object>) payloadCaptor.getAllValues().get(0);
        Map<String, Object> p2 = (Map<String, Object>) payloadCaptor.getAllValues().get(1);
        assertThat(p1).containsEntry("memberId", 100L).containsEntry("presence", "busy");
        assertThat(p2).containsEntry("memberId", 200L).containsEntry("presence", "active");
    }

    @Test
    @DisplayName("broadcastUserPresenceToAllWorkspaces(offline): 모든 활성 워크스페이스에 offline을 보낸다")
    @SuppressWarnings("unchecked")
    void broadcastUserPresenceOffline() {
        User user = user(10L, "alice@test.com");
        Workspace w1 = workspace(1L);
        WorkspaceMember m1 = workspaceMember(100L, w1, user, "owner");
        ReflectionTestUtils.setField(m1, "isActive", true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(m1));

        workspaceService.broadcastUserPresenceToAllWorkspaces(10L, false);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/workspaces/1/presence"),
                payloadCaptor.capture()
        );
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(payload).containsEntry("memberId", 100L).containsEntry("presence", "offline");
    }

    @Test
    @DisplayName("getMyWorkspaces: membersOnline은 본인을 제외한 접속 멤버 수다(본인은 FE가 더함)")
    void getMyWorkspacesWithOnlineCount() {
        User me = user(10L, "me@test.com");
        User other = user(20L, "other@test.com");
        Workspace ws = workspace(1L);
        WorkspaceMember myMembership = workspaceMember(100L, ws, me, "owner");
        WorkspaceMember otherMembership = workspaceMember(200L, ws, other, "editor");
        ReflectionTestUtils.setField(myMembership, "isActive", true);
        ReflectionTestUtils.setField(otherMembership, "isActive", true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(me));
        when(workspaceMemberRepository.findAllByUser(me)).thenReturn(List.of(myMembership));
        when(workspaceMemberRepository.countByWorkspaceAndIsActiveTrue(ws)).thenReturn(2);
        when(workspaceMemberRepository.findAllByWorkspace(ws)).thenReturn(List.of(myMembership, otherMembership));
        // 본인(10L)은 제외되어 isOnline 조회 안 함. 다른 멤버(20L)만 registry로 판단(접속 중).
        when(presenceRegistry.isOnline(20L)).thenReturn(true);

        List<WorkspaceResponse> result = workspaceService.getMyWorkspaces(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemberCount()).isEqualTo(2);
        assertThat(result.get(0).getMembersOnline()).isEqualTo(1); // 본인 제외, 다른 멤버 1명
    }

    @Test
    @DisplayName("updatePresence: 사용자의 모든 활성 워크스페이스에 적용하고 각 토픽에 브로드캐스트한다(전역)")
    void updatePresenceAppliesToAllWorkspaces() {
        User user = user(10L, "alice@test.com");
        Workspace w1 = workspace(1L);
        Workspace w2 = workspace(2L);
        WorkspaceMember m1 = workspaceMember(100L, w1, user, "owner");
        WorkspaceMember m2 = workspaceMember(200L, w2, user, "editor");
        ReflectionTestUtils.setField(m1, "isActive", true);
        ReflectionTestUtils.setField(m2, "isActive", true);
        // 요청 워크스페이스(1L) 권한 검증 경로
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(w1));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspaceAndUser(w1, user)).thenReturn(Optional.of(m1));
        // 전역 적용 루프
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(m1, m2));
        when(preferencesRepository.findByWorkspaceMember(m1)).thenReturn(Optional.empty());
        when(preferencesRepository.findByWorkspaceMember(m2)).thenReturn(Optional.empty());

        workspaceService.updatePresence(1L, "busy", 10L);

        verify(preferencesRepository, org.mockito.Mockito.times(2)).save(any(WorkspaceMemberPreferences.class));
        ArgumentCaptor<String> destCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, org.mockito.Mockito.times(2))
                .convertAndSend(destCaptor.capture(), payloadCaptor.capture());
        assertThat(destCaptor.getAllValues())
                .containsExactlyInAnyOrder("/topic/workspaces/1/presence", "/topic/workspaces/2/presence");
    }

    @Test
    @DisplayName("getMembers: 접속 세션 없는 멤버는 고른 상태와 무관하게 offline로 내려준다")
    void getMembersOverlaysLiveness() {
        User me = user(10L, "me@test.com");
        User other = user(20L, "other@test.com");
        Workspace ws = workspace(1L);
        WorkspaceMember myMembership = workspaceMember(100L, ws, me, "owner");
        WorkspaceMember otherMembership = workspaceMember(200L, ws, other, "editor");
        ReflectionTestUtils.setField(myMembership, "isActive", true);
        ReflectionTestUtils.setField(otherMembership, "isActive", true);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(ws));
        when(userRepository.findById(10L)).thenReturn(Optional.of(me));
        when(workspaceMemberRepository.findByWorkspaceAndUser(ws, me)).thenReturn(Optional.of(myMembership));
        when(workspaceMemberRepository.findAllByWorkspace(ws)).thenReturn(List.of(myMembership, otherMembership));
        when(presenceRegistry.isOnline(10L)).thenReturn(true);   // 본인 접속 중
        when(presenceRegistry.isOnline(20L)).thenReturn(false);  // 다른 멤버 세션 없음
        when(preferencesRepository.findByWorkspaceMember(myMembership)).thenReturn(Optional.empty()); // active

        List<WorkspaceMemberResponse> result = workspaceService.getMembers(1L, 10L);

        WorkspaceMemberResponse meResp = result.stream()
                .filter(r -> r.getMemberId().equals(100L)).findFirst().orElseThrow();
        WorkspaceMemberResponse otherResp = result.stream()
                .filter(r -> r.getMemberId().equals(200L)).findFirst().orElseThrow();
        assertThat(meResp.getPresence()).isEqualTo("active");
        assertThat(otherResp.getPresence()).isEqualTo("offline"); // 세션 없으면 고른 상태 무관하게 offline
    }

    private static User user(Long id, String email) {
        User user = User.create(email, "hashed-password", email);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static User user(long id, String email, String githubEmail) {
        User user = User.create(email, "hash", "name");
        ReflectionTestUtils.setField(user, "id", id);
        if (githubEmail != null) {
            ReflectionTestUtils.setField(user, "githubEmail", githubEmail);
        }
        return user;
    }

    private static Workspace workspace(Long id) {
        User owner = user(999L, "owner@test.com");
        Workspace workspace = Workspace.create(owner, "Team", "team-" + id, null);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember workspaceMember(Long id, Workspace workspace, User user, String authority) {
        WorkspaceMember member = WorkspaceMember.create(workspace, user, authority);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @SuppressWarnings("unchecked")
    private void assertPresenceBroadcast(
            String expectedDestination,
            Long expectedWorkspaceId,
            WorkspaceMember member,
            User user,
            String expectedPresence
    ) {
        ArgumentCaptor<String> destinationCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        verify(messagingTemplate).convertAndSend(destinationCaptor.capture(), payloadCaptor.capture());

        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertThat(destinationCaptor.getValue()).isEqualTo(expectedDestination);
        assertThat(payload)
                .containsEntry("workspaceId", expectedWorkspaceId)
                .containsEntry("memberId", member.getId())
                .containsEntry("workspaceMemberId", member.getId())
                .containsEntry("userId", user.getId())
                .containsEntry("username", user.getUsername())
                .containsEntry("presence", expectedPresence);
        verifyNoMoreInteractions(messagingTemplate);
    }

    private static Invitation pendingInvitation(String invitedEmail) {
        return pendingInvitation(invitedEmail, null);
    }

    private static Invitation pendingInvitation(String invitedEmail, String invitedPosition) {
        User owner = user(99L, "owner@x.com", null);
        Workspace workspace = Workspace.create(owner, "WS", "ws", "");
        WorkspaceMember inviter = WorkspaceMember.create(workspace, owner, "owner");
        return Invitation.create(workspace, inviter, invitedEmail, "viewer", invitedPosition, "tok", LocalDateTime.now().plusDays(1));
    }
}
