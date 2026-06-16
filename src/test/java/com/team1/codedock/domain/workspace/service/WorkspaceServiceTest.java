package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.repository.InvitationRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberPreferencesRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private InvitationRepository invitationRepository;
    @Mock private WorkspaceMemberPreferencesRepository preferencesRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WorkspaceService workspaceService;

    @Test
    @DisplayName("초대 수락: invitedEmail이 사용자의 기본 email과 일치하면 수락된다")
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
    @DisplayName("초대 수락: 기본 email은 불일치하나 githubEmail이 일치하면 수락된다")
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
    @DisplayName("초대 수락: 기본 email 소유 계정이 우선하므로 githubEmail만 일치하는 다른 사용자는 거부된다")
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
    @DisplayName("초대 수락: 매칭되는 계정이 없으면 거부된다")
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
    @DisplayName("초대 수락: githubEmail 다중 일치 시 가장 오래된 계정이 소유자이며 그 외 계정은 거부된다")
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

    private User user(long id, String email, String githubEmail) {
        User u = User.create(email, "hash", "name");
        ReflectionTestUtils.setField(u, "id", id);
        if (githubEmail != null) {
            ReflectionTestUtils.setField(u, "githubEmail", githubEmail);
        }
        return u;
    }

    private Invitation pendingInvitation(String invitedEmail) {
        User owner = user(99L, "owner@x.com", null);
        Workspace ws = Workspace.create(owner, "WS", "ws", "");
        return Invitation.create(ws, null, invitedEmail, "viewer", "tok", LocalDateTime.now().plusDays(1));
    }
}