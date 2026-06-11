package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChannelReadStatusServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private ChannelReadStatusRepository channelReadStatusRepository;

    @InjectMocks
    private ChannelReadStatusService channelReadStatusService;

    @Test
    @DisplayName("Creates read status when member reads channel for first time")
    void markChannelAsReadCreatesStatus() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace);
        Thread latestThread = thread(100L, channel, member, "latest");

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findFirstByChannel_IdAndThreadTypeOrderByIdDesc(1L, Thread.TYPE_USER_MESSAGE))
                .thenReturn(Optional.of(latestThread));
        when(channelReadStatusRepository.findByChannel_IdAndWorkspaceMember_Id(1L, 20L))
                .thenReturn(Optional.empty());
        when(channelReadStatusRepository.save(org.mockito.ArgumentMatchers.any(ChannelReadStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = channelReadStatusService.markChannelAsRead(1L, 30L);

        assertThat(response.channelId()).isEqualTo(1L);
        assertThat(response.workspaceMemberId()).isEqualTo(20L);
        assertThat(response.lastReadThreadId()).isEqualTo(100L);
        assertThat(response.lastReadAt()).isNotNull();

        ArgumentCaptor<ChannelReadStatus> captor = ArgumentCaptor.forClass(ChannelReadStatus.class);
        verify(channelReadStatusRepository).save(captor.capture());
        assertThat(captor.getValue().getLastReadThread()).isEqualTo(latestThread);
    }

    @Test
    @DisplayName("Updates existing read status")
    void markChannelAsReadUpdatesStatus() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace);
        Thread oldThread = thread(99L, channel, member, "old");
        Thread latestThread = thread(100L, channel, member, "latest");
        ChannelReadStatus status = ChannelReadStatus.create(
                channel,
                member,
                oldThread,
                LocalDateTime.of(2026, 6, 10, 10, 0)
        );

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findFirstByChannel_IdAndThreadTypeOrderByIdDesc(1L, Thread.TYPE_USER_MESSAGE))
                .thenReturn(Optional.of(latestThread));
        when(channelReadStatusRepository.findByChannel_IdAndWorkspaceMember_Id(1L, 20L))
                .thenReturn(Optional.of(status));

        var response = channelReadStatusService.markChannelAsRead(1L, 30L);

        assertThat(response.lastReadThreadId()).isEqualTo(100L);
        assertThat(status.getLastReadThread()).isEqualTo(latestThread);
        assertThat(status.getLastReadAt()).isAfter(LocalDateTime.of(2026, 6, 10, 10, 0));
        verify(channelReadStatusRepository, never()).save(org.mockito.ArgumentMatchers.any(ChannelReadStatus.class));
    }

    @Test
    @DisplayName("Allows marking empty channel as read")
    void markEmptyChannelAsRead() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace);

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findFirstByChannel_IdAndThreadTypeOrderByIdDesc(1L, Thread.TYPE_USER_MESSAGE))
                .thenReturn(Optional.empty());
        when(channelReadStatusRepository.findByChannel_IdAndWorkspaceMember_Id(1L, 20L))
                .thenReturn(Optional.empty());
        when(channelReadStatusRepository.save(org.mockito.ArgumentMatchers.any(ChannelReadStatus.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = channelReadStatusService.markChannelAsRead(1L, 30L);

        assertThat(response.lastReadThreadId()).isNull();
        assertThat(response.lastReadAt()).isNotNull();
    }

    @Test
    @DisplayName("Rejects read status without user")
    void markChannelAsReadWithoutUser() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> channelReadStatusService.markChannelAsRead(1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects read status by non workspace member")
    void markChannelAsReadByNonMember() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> channelReadStatusService.markChannelAsRead(1L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Rejects read status for unknown channel")
    void markUnknownChannelAsRead() {
        when(channelRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> channelReadStatusService.markChannelAsRead(1L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);
    }

    private Workspace workspace(Long id) {
        Workspace workspace = mock(Workspace.class);
        lenient().when(workspace.getId()).thenReturn(id);
        return workspace;
    }

    private Channel channel(Long id, Workspace workspace) {
        Channel channel = Channel.createCustom(workspace, "team-chat", null);
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private WorkspaceMember workspaceMember(Long id, Workspace workspace) {
        WorkspaceMember member = WorkspaceMember.create(workspace, mock(User.class), "editor");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Thread thread(Long id, Channel channel, WorkspaceMember member, String content) {
        Thread thread = Thread.createChannelMessage(channel, member, content);
        ReflectionTestUtils.setField(thread, "id", id);
        return thread;
    }
}
