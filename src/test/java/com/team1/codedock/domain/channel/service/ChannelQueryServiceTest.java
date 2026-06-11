package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ChannelMessageCountProjection;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelQueryServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ThreadRepository threadRepository;

    @InjectMocks
    private ChannelQueryService channelQueryService;

    @Test
    @DisplayName("Returns channels for active workspace member")
    void getChannels() {
        Workspace workspace = workspace(10L);
        Channel channel = Channel.createCustom(workspace, "team-chat", "Team chat");
        ReflectionTestUtils.setField(channel, "id", 1L);
        Thread latestMessage = Thread.createChannelMessage(channel, mock(WorkspaceMember.class), "latest");
        ReflectionTestUtils.setField(latestMessage, "id", 11L);
        ReflectionTestUtils.setField(latestMessage, "createdAt", LocalDateTime.of(2026, 6, 11, 10, 0));
        ChannelMessageCountProjection messageCount = messageCount(1L, 3L);
        ChannelMessageCountProjection unreadCount = messageCount(1L, 2L);
        WorkspaceMember member = workspaceMember(200L);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(member));
        when(channelRepository.findAllByWorkspace_IdOrderByIdAsc(10L)).thenReturn(List.of(channel));
        when(threadRepository.countByChannelIdsAndThreadType(List.of(1L), Thread.TYPE_USER_MESSAGE))
                .thenReturn(List.of(messageCount));
        when(threadRepository.countUnreadByChannelIdsAndThreadType(List.of(1L), Thread.TYPE_USER_MESSAGE, 200L))
                .thenReturn(List.of(unreadCount));
        when(threadRepository.findLatestByChannelIdsAndThreadType(List.of(1L), Thread.TYPE_USER_MESSAGE))
                .thenReturn(List.of(latestMessage));

        var response = channelQueryService.getChannels(10L, 100L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(0).name()).isEqualTo("team-chat");
        assertThat(response.get(0).lastMessage()).isEqualTo("latest");
        assertThat(response.get(0).lastMessageAt()).isEqualTo(LocalDateTime.of(2026, 6, 11, 10, 0));
        assertThat(response.get(0).messageCount()).isEqualTo(3L);
        assertThat(response.get(0).unreadCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("Returns null last message and zero count for channel without messages")
    void getChannelsWithoutMessages() {
        Workspace workspace = workspace(10L);
        Channel channel = Channel.createCustom(workspace, "empty", null);
        ReflectionTestUtils.setField(channel, "id", 2L);
        WorkspaceMember member = workspaceMember(200L);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(member));
        when(channelRepository.findAllByWorkspace_IdOrderByIdAsc(10L)).thenReturn(List.of(channel));
        when(threadRepository.countByChannelIdsAndThreadType(List.of(2L), Thread.TYPE_USER_MESSAGE))
                .thenReturn(List.of());
        when(threadRepository.countUnreadByChannelIdsAndThreadType(List.of(2L), Thread.TYPE_USER_MESSAGE, 200L))
                .thenReturn(List.of());
        when(threadRepository.findLatestByChannelIdsAndThreadType(List.of(2L), Thread.TYPE_USER_MESSAGE))
                .thenReturn(List.of());

        var response = channelQueryService.getChannels(10L, 100L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).lastMessage()).isNull();
        assertThat(response.get(0).lastMessageAt()).isNull();
        assertThat(response.get(0).messageCount()).isZero();
        assertThat(response.get(0).unreadCount()).isZero();
    }

    @Test
    @DisplayName("Rejects channel list without user")
    void getChannelsWithoutUser() {
        assertThatThrownBy(() -> channelQueryService.getChannels(10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(channelRepository, never()).findAllByWorkspace_IdOrderByIdAsc(10L);
    }

    @Test
    @DisplayName("Rejects channel list by non workspace member")
    void getChannelsByNonWorkspaceMember() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> channelQueryService.getChannels(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).findAllByWorkspace_IdOrderByIdAsc(10L);
    }

    private Workspace workspace(Long id) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(id);
        return workspace;
    }

    private ChannelMessageCountProjection messageCount(Long channelId, long messageCount) {
        ChannelMessageCountProjection projection = mock(ChannelMessageCountProjection.class);
        when(projection.getChannelId()).thenReturn(channelId);
        when(projection.getMessageCount()).thenReturn(messageCount);
        return projection;
    }

    private WorkspaceMember workspaceMember(Long id) {
        WorkspaceMember member = mock(WorkspaceMember.class);
        when(member.getId()).thenReturn(id);
        return member;
    }
}
