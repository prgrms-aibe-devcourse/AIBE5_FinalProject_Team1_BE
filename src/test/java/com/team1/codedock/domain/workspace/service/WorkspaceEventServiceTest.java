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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class WorkspaceEventServiceTest {

    @Mock private WorkspaceEventRepository workspaceEventRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private WorkspaceEventService workspaceEventService;

    @Test
    @DisplayName("이벤트를 저장하고 워크스페이스 lastActivityAt을 갱신한다")
    void recordEvent_성공() {
        Workspace workspace = workspace(10L);
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(workspaceEventRepository.save(any(WorkspaceEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        workspaceEventService.recordEvent(
                10L, WorkspaceEvent.EventType.MENTION, "actor", null, null, 1L, "hello", null, null, null, null, null);

        ArgumentCaptor<WorkspaceEvent> captor = ArgumentCaptor.forClass(WorkspaceEvent.class);
        verify(workspaceEventRepository).save(captor.capture());
        WorkspaceEvent saved = captor.getValue();
        assertThat(saved.getWorkspace()).isEqualTo(workspace);
        assertThat(saved.getType()).isEqualTo(WorkspaceEvent.EventType.MENTION);
        assertThat(saved.getActorName()).isEqualTo("actor");
        assertThat(saved.getChannelId()).isEqualTo(1L);
        assertThat(saved.getContent()).isEqualTo("hello");
        assertThat(saved.getRepositoryId()).isNull();
        assertThat(saved.getThreadId()).isNull();
        assertThat(workspace.getLastActivityAt()).isNotNull();
    }

    @Test
    @DisplayName("워크스페이스가 없으면 WORKSPACE_NOT_FOUND 예외가 발생한다")
    void recordEvent_워크스페이스없음() {
        when(workspaceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceEventService.recordEvent(99L, WorkspaceEvent.EventType.REPLY, "actor", null, null, null, "hi", null, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORKSPACE_NOT_FOUND);

        verify(workspaceEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자가 속한 모든 워크스페이스의 이벤트를 최신순으로 반환한다")
    void getEventsForUser_성공() {
        User user = user(1L);
        Workspace workspace = workspace(10L);
        WorkspaceMember member = WorkspaceMember.create(workspace, user, "editor");
        ReflectionTestUtils.setField(member, "id", 20L);

        WorkspaceEvent event = WorkspaceEvent.create(workspace, WorkspaceEvent.EventType.MENTION,
                "actor", null, null, 1L, "hello", null, null, null, null, null);
        ReflectionTestUtils.setField(event, "id", 100L);
        ReflectionTestUtils.setField(event, "createdAt", LocalDateTime.of(2026, 6, 18, 12, 0));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of(member));
        when(workspaceEventRepository.findAllByWorkspace_IdInOrderByCreatedAtDesc(List.of(10L)))
                .thenReturn(List.of(event));

        List<WorkspaceEventResponse> result = workspaceEventService.getEventsForUser(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(100L);
        assertThat(result.get(0).workspaceId()).isEqualTo(10L);
        assertThat(result.get(0).type()).isEqualTo("MENTION");
        assertThat(result.get(0).actorName()).isEqualTo("actor");
        assertThat(result.get(0).channelId()).isEqualTo(1L);
        assertThat(result.get(0).content()).isEqualTo("hello");
    }

    @Test
    @DisplayName("활성 멤버십이 없으면 빈 리스트를 반환한다")
    void getEventsForUser_멤버십없음_빈리스트() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser(user)).thenReturn(List.of());

        List<WorkspaceEventResponse> result = workspaceEventService.getEventsForUser(1L);

        assertThat(result).isEmpty();
        verify(workspaceEventRepository, never()).findAllByWorkspace_IdInOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("사용자가 없으면 USER_NOT_FOUND 예외가 발생한다")
    void getEventsForUser_유저없음() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceEventService.getEventsForUser(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    private static User user(Long id) {
        User user = User.create("user@test.com", "hashed", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static Workspace workspace(Long id) {
        User owner = User.create("owner@test.com", "hashed", "owner");
        Workspace workspace = Workspace.create(owner, "Team", "team-" + id, null);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }
}
