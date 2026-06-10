package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ChannelCommandServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private ChannelReadStatusRepository channelReadStatusRepository;

    @Mock
    private GithubPullRequestRepository githubPullRequestRepository;

    @Mock
    private GithubIssueRepository githubIssueRepository;

    @InjectMocks
    private ChannelCommandService channelCommandService;

    @BeforeEach
    void setUp() {
        WorkspaceMember manager = workspaceMember("admin");
        lenient().when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(any(), any()))
                .thenReturn(Optional.of(manager));
    }

    @Test
    @DisplayName("Rejects creating channel without user")
    void createChannelWithoutUser() {
        assertThatThrownBy(() ->
                channelCommandService.createChannel(10L, null, new ChannelCreateRequest("team-chat", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects creating channel by non workspace member")
    void createChannelByNonWorkspaceMember() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest("team-chat", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects creating channel by viewer")
    void createChannelByViewer() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() ->
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest("team-chat", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects updating channel by viewer")
    void updateChannelByViewer() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() ->
                channelCommandService.updateChannel(10L, 2L, 100L, new ChannelUpdateRequest("renamed", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).findById(2L);
    }

    @Test
    @DisplayName("Rejects deleting channel by viewer")
    void deleteChannelByViewer() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Creates a custom channel")
    void createChannel() {
        Workspace workspace = workspace(10L);
        Channel saved = channel(2L, workspace, "team-chat", true);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(channelRepository.existsByWorkspace_IdAndNameIgnoreCase(10L, "team-chat")).thenReturn(false);
        when(channelRepository.save(any(Channel.class))).thenReturn(saved);

        ChannelListResponse response =
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest(" team-chat ", "Team chat"));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("team-chat");
        assertThat(response.channelType()).isEqualTo("custom");
    }

    @Test
    @DisplayName("Rejects duplicate channel name on create")
    void createChannelWithDuplicateName() {
        Workspace workspace = mock(Workspace.class);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(channelRepository.existsByWorkspace_IdAndNameIgnoreCase(10L, "team-chat")).thenReturn(true);

        assertThatThrownBy(() ->
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest("team-chat", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel name already exists");

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("Updates channel name and description")
    void updateChannel() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "old-name", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(channelRepository.existsByWorkspace_IdAndNameIgnoreCaseAndIdNot(10L, "new-name", 2L)).thenReturn(false);

        ChannelListResponse response =
                channelCommandService.updateChannel(10L, 2L, 100L, new ChannelUpdateRequest(" new-name ", "Updated"));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("new-name");
        assertThat(response.description()).isEqualTo("Updated");
    }

    @Test
    @DisplayName("Rejects duplicate channel name on update")
    void updateChannelWithDuplicateName() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "old-name", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(channelRepository.existsByWorkspace_IdAndNameIgnoreCaseAndIdNot(10L, "general", 2L)).thenReturn(true);

        assertThatThrownBy(() ->
                channelCommandService.updateChannel(10L, 2L, 100L, new ChannelUpdateRequest("general", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel name already exists");
    }

    @Test
    @DisplayName("Rejects updating non-deletable channel")
    void updateNonDeletableChannel() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "general", false);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() ->
                channelCommandService.updateChannel(10L, 2L, 100L, new ChannelUpdateRequest("renamed", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Deletes deletable channel")
    void deleteChannel() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(threadRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(channelReadStatusRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(githubPullRequestRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(githubIssueRepository.existsByChannel_Id(2L)).thenReturn(false);

        channelCommandService.deleteChannel(10L, 2L, 100L);

        verify(channelRepository).delete(channel);
    }

    @Test
    @DisplayName("Rejects deleting non-deletable channel")
    void deleteNonDeletableChannel() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "general", false);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects deleting channel with messages")
    void deleteChannelWithMessages() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(threadRepository.existsByChannel_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel with messages cannot be deleted");

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects deleting channel with read status")
    void deleteChannelWithReadStatus() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(threadRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(channelReadStatusRepository.existsByChannel_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel with read status cannot be deleted");

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects deleting channel with pull requests")
    void deleteChannelWithPullRequests() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(threadRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(channelReadStatusRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(githubPullRequestRepository.existsByChannel_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel with pull requests cannot be deleted");

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects deleting channel with issues")
    void deleteChannelWithIssues() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(threadRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(channelReadStatusRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(githubPullRequestRepository.existsByChannel_Id(2L)).thenReturn(false);
        when(githubIssueRepository.existsByChannel_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Channel with issues cannot be deleted");

        verify(channelRepository, never()).delete(any(Channel.class));
    }

    private Workspace workspace(Long id) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(id);
        return workspace;
    }

    private Channel channel(Long id, Workspace workspace, String name, boolean isDeletable) {
        Channel channel = Channel.createCustom(workspace, name, null);
        ReflectionTestUtils.setField(channel, "id", id);
        ReflectionTestUtils.setField(channel, "isDeletable", isDeletable);
        return channel;
    }

    private WorkspaceMember workspaceMember(String authority) {
        WorkspaceMember member = mock(WorkspaceMember.class);
        lenient().when(member.getAuthority()).thenReturn(authority);
        return member;
    }
}
