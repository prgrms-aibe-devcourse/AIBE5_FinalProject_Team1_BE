package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.dto.WorkspaceCreateRequest;
import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private static Invitation pendingInvitation(String invitedEmail) {
        User owner = user(99L, "owner@x.com", null);
        Workspace workspace = Workspace.create(owner, "WS", "ws", "");
        return Invitation.create(workspace, null, invitedEmail, "viewer", "tok", LocalDateTime.now().plusDays(1));
    }
}
