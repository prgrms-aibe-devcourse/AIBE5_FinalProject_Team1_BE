package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
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

    @InjectMocks
    private ChannelQueryService channelQueryService;

    @Test
    @DisplayName("Returns channels for active workspace member")
    void getChannels() {
        Workspace workspace = workspace(10L);
        Channel channel = Channel.createCustom(workspace, "team-chat", "Team chat");
        ReflectionTestUtils.setField(channel, "id", 1L);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(channelRepository.findAllByWorkspace_IdOrderByIdAsc(10L)).thenReturn(List.of(channel));

        var response = channelQueryService.getChannels(10L, 100L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(1L);
        assertThat(response.get(0).name()).isEqualTo("team-chat");
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
}
