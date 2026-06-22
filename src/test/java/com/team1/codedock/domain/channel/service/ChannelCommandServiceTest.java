package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelOrderUpdateRequest;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.BookmarkRepository;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.MentionRepository;
import com.team1.codedock.domain.chat.repository.ReactionRepository;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
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
    private ThreadReplyRepository threadReplyRepository;

    @Mock
    private ThreadAttachmentRepository threadAttachmentRepository;

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private MentionRepository mentionRepository;

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
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "team-chat")).thenReturn(0L);
        when(channelRepository.findMaxDisplayOrderByWorkspaceId(10L)).thenReturn(4);
        when(channelRepository.save(any(Channel.class))).thenReturn(saved);

        ChannelListResponse response =
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest(" team-chat ", "Team chat"));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("team-chat");
        assertThat(response.channelType()).isEqualTo("custom");

        ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(channelCaptor.capture());
        assertThat(channelCaptor.getValue().getDisplayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("Creates a custom channel when manager authority has mixed case or spaces")
    void createChannelWithNormalizedManagerAuthority() {
        WorkspaceMember manager = workspaceMember(" ADMIN ");
        Workspace workspace = workspace(10L);
        Channel saved = channel(2L, workspace, "team-chat", true);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(manager));
        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "team-chat")).thenReturn(0L);
        when(channelRepository.save(any(Channel.class))).thenReturn(saved);

        ChannelListResponse response =
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest("team-chat", null));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("team-chat");
    }

    @Test
    @DisplayName("채널 순서를 요청 순서대로 변경한다")
    void updateChannelOrder() {
        Workspace workspace = workspace(10L);
        Channel first = channel(1L, workspace, "general", false);
        Channel second = channel(2L, workspace, "repository", false);
        Channel third = channel(3L, workspace, "docs", true);

        when(channelRepository.findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(first, second, third));

        List<ChannelListResponse> responses = channelCommandService.updateChannelOrder(
                10L,
                100L,
                new ChannelOrderUpdateRequest(List.of(3L, 1L, 2L))
        );

        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(2);
        assertThat(third.getDisplayOrder()).isZero();
        assertThat(responses).extracting(ChannelListResponse::id).containsExactly(3L, 1L, 2L);
        assertThat(responses).extracting(ChannelListResponse::displayOrder).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("채널 순서 변경은 중복된 채널 id를 거부한다")
    void updateChannelOrderWithDuplicateChannelId() {
        assertThatThrownBy(() -> channelCommandService.updateChannelOrder(
                10L,
                100L,
                new ChannelOrderUpdateRequest(List.of(1L, 1L, 2L))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("중복된 채널");

        verify(channelRepository, never()).findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(10L);
    }

    @Test
    @DisplayName("채널 순서 변경은 전체 채널 목록이 아니면 거부한다")
    void updateChannelOrderWithMissingWorkspaceChannel() {
        Workspace workspace = workspace(10L);
        Channel first = channel(1L, workspace, "general", false);
        Channel second = channel(2L, workspace, "docs", true);

        when(channelRepository.findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> channelCommandService.updateChannelOrder(
                10L,
                100L,
                new ChannelOrderUpdateRequest(List.of(2L))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("모든 채널");

        assertThat(first.getDisplayOrder()).isZero();
        assertThat(second.getDisplayOrder()).isZero();
    }

    @Test
    @DisplayName("채널 순서 변경은 다른 워크스페이스 채널 id를 거부한다")
    void updateChannelOrderWithForeignChannelId() {
        Workspace workspace = workspace(10L);
        Channel first = channel(1L, workspace, "general", false);
        Channel second = channel(2L, workspace, "docs", true);

        when(channelRepository.findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(10L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> channelCommandService.updateChannelOrder(
                10L,
                100L,
                new ChannelOrderUpdateRequest(List.of(1L, 99L))
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("다른 워크스페이스");
    }

    @Test
    @DisplayName("채널 순서 변경은 권한 없는 사용자를 거부한다")
    void updateChannelOrderByViewer() {
        WorkspaceMember viewer = workspaceMember("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> channelCommandService.updateChannelOrder(
                10L,
                100L,
                new ChannelOrderUpdateRequest(List.of(1L, 2L))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(channelRepository, never()).findAllByWorkspace_IdOrderByDisplayOrderAscIdAsc(10L);
    }

    @Test
    @DisplayName("Rejects duplicate channel name on create")
    void createChannelWithDuplicateName() {
        Workspace workspace = mock(Workspace.class);

        when(workspaceRepository.findById(10L)).thenReturn(Optional.of(workspace));
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCase(10L, "team-chat")).thenReturn(1L);

        assertThatThrownBy(() ->
                channelCommandService.createChannel(10L, 100L, new ChannelCreateRequest("team-chat", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 같은 이름의 채널");

        verify(channelRepository, never()).save(any(Channel.class));
    }

    @Test
    @DisplayName("Updates channel name and description")
    void updateChannel() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "old-name", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCaseAndIdNot(10L, "new-name", 2L)).thenReturn(0L);

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
        when(channelRepository.countByWorkspaceIdAndNameIgnoreCaseAndIdNot(10L, "general", 2L)).thenReturn(1L);

        assertThatThrownBy(() ->
                channelCommandService.updateChannel(10L, 2L, 100L, new ChannelUpdateRequest("general", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 같은 이름의 채널");
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
        when(githubPullRequestRepository.countByChannelId(2L)).thenReturn(0L);
        when(githubIssueRepository.countByChannelId(2L)).thenReturn(0L);

        channelCommandService.deleteChannel(10L, 2L, 100L);

        InOrder inOrder = inOrder(
                channelReadStatusRepository,
                reactionRepository,
                mentionRepository,
                bookmarkRepository,
                threadAttachmentRepository,
                threadReplyRepository,
                threadRepository,
                channelRepository
        );
        inOrder.verify(channelReadStatusRepository).clearLastReadThreadByThreadChannelId(2L);
        inOrder.verify(channelReadStatusRepository).deleteAllByChannelId(2L);
        inOrder.verify(reactionRepository).deleteAllThreadReplyReactionsByChannelId(2L);
        inOrder.verify(reactionRepository).deleteAllThreadReactionsByChannelId(2L);
        inOrder.verify(mentionRepository).deleteAllByThreadReplyChannelId(2L);
        inOrder.verify(mentionRepository).deleteAllByThreadChannelId(2L);
        inOrder.verify(bookmarkRepository).deleteAllByThreadChannelId(2L);
        inOrder.verify(threadAttachmentRepository).deleteAllByThreadChannelId(2L);
        inOrder.verify(threadReplyRepository).clearPullRequestReviewReferencesByThreadChannelId(2L);
        inOrder.verify(threadReplyRepository).clearPullRequestReviewCommentReferencesByThreadChannelId(2L);
        inOrder.verify(threadReplyRepository).deleteAllByThreadChannelId(2L);
        inOrder.verify(threadRepository).clearReplyToByChannelId(2L);
        inOrder.verify(threadRepository).deleteAllByChannelId(2L);
        inOrder.verify(channelRepository).delete(channel);
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
    @DisplayName("Deletes deletable channel with messages and chat data")
    void deleteChannelWithMessages() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.countByChannelId(2L)).thenReturn(0L);
        when(githubIssueRepository.countByChannelId(2L)).thenReturn(0L);

        channelCommandService.deleteChannel(10L, 2L, 100L);

        verify(threadReplyRepository).deleteAllByThreadChannelId(2L);
        verify(threadRepository).deleteAllByChannelId(2L);
        verify(channelRepository).delete(channel);
    }

    @Test
    @DisplayName("Deletes empty channel after removing read status")
    void deleteChannelWithReadStatus() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.countByChannelId(2L)).thenReturn(0L);
        when(githubIssueRepository.countByChannelId(2L)).thenReturn(0L);

        channelCommandService.deleteChannel(10L, 2L, 100L);

        InOrder inOrder = inOrder(channelReadStatusRepository, channelRepository);
        inOrder.verify(channelReadStatusRepository).clearLastReadThreadByThreadChannelId(2L);
        inOrder.verify(channelReadStatusRepository).deleteAllByChannelId(2L);
        inOrder.verify(channelRepository).delete(channel);
    }

    @Test
    @DisplayName("Rejects deleting channel with pull requests")
    void deleteChannelWithPullRequests() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.countByChannelId(2L)).thenReturn(1L);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Pull Request가 연결된 채널");

        verify(channelReadStatusRepository, never()).deleteAllByChannelId(2L);
        verify(channelRepository, never()).delete(any(Channel.class));
    }

    @Test
    @DisplayName("Rejects deleting channel with issues")
    void deleteChannelWithIssues() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(2L, workspace, "team-chat", true);

        when(channelRepository.findById(2L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.countByChannelId(2L)).thenReturn(0L);
        when(githubIssueRepository.countByChannelId(2L)).thenReturn(1L);

        assertThatThrownBy(() -> channelCommandService.deleteChannel(10L, 2L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Issue가 연결된 채널");

        verify(channelReadStatusRepository, never()).deleteAllByChannelId(2L);
        verify(channelRepository, never()).delete(any(Channel.class));
    }

    private Workspace workspace(Long id) {
        Workspace workspace = mock(Workspace.class);
        lenient().when(workspace.getId()).thenReturn(id);
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
