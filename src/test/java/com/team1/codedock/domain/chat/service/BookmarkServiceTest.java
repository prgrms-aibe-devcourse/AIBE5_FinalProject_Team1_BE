package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.entity.Bookmark;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.BookmarkRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @InjectMocks
    private BookmarkService bookmarkService;

    @Test
    @DisplayName("Creates bookmark when message is not bookmarked")
    void toggleMessageBookmarkCreatesBookmark() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace, user("sender"));
        Thread message = message(100L, channel, member, "hello");

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(bookmarkRepository.findByWorkspaceMember_IdAndThread_Id(20L, 100L)).thenReturn(Optional.empty());

        var response = bookmarkService.toggleMessageBookmark(1L, 100L, 30L);

        assertThat(response.channelId()).isEqualTo(1L);
        assertThat(response.messageId()).isEqualTo(100L);
        assertThat(response.workspaceMemberId()).isEqualTo(20L);
        assertThat(response.bookmarked()).isTrue();

        ArgumentCaptor<Bookmark> captor = ArgumentCaptor.forClass(Bookmark.class);
        verify(bookmarkRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkspaceMember()).isEqualTo(member);
        assertThat(captor.getValue().getThread()).isEqualTo(message);
    }

    @Test
    @DisplayName("Deletes bookmark when message is already bookmarked")
    void toggleMessageBookmarkDeletesBookmark() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace, user("sender"));
        Thread message = message(100L, channel, member, "hello");
        Bookmark bookmark = Bookmark.create(member, message);

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(bookmarkRepository.findByWorkspaceMember_IdAndThread_Id(20L, 100L)).thenReturn(Optional.of(bookmark));

        var response = bookmarkService.toggleMessageBookmark(1L, 100L, 30L);

        assertThat(response.bookmarked()).isFalse();
        verify(bookmarkRepository).delete(bookmark);
        verify(bookmarkRepository, never()).save(org.mockito.ArgumentMatchers.any(Bookmark.class));
    }

    @Test
    @DisplayName("Returns my bookmarks in workspace")
    void getMyBookmarks() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace, user("sender"));
        Thread message = message(100L, channel, member, ChatContentEmojiCodec.encode("hello 👍🔥"));
        ReflectionTestUtils.setField(message, "createdAt", LocalDateTime.of(2026, 6, 11, 10, 0));
        Bookmark bookmark = Bookmark.create(member, message);
        ReflectionTestUtils.setField(bookmark, "id", 200L);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 6, 11, 11, 0));

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(bookmarkRepository.findAllByWorkspaceMember_IdOrderByCreatedAtDesc(20L)).thenReturn(List.of(bookmark));

        var response = bookmarkService.getMyBookmarks(10L, 30L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).bookmarkId()).isEqualTo(200L);
        assertThat(response.get(0).channelId()).isEqualTo(1L);
        assertThat(response.get(0).messageId()).isEqualTo(100L);
        assertThat(message.getContent()).isEqualTo("hello [[emoji:like]][[emoji:fire]]");
        assertThat(response.get(0).content()).isEqualTo("hello 👍🔥");
        assertThat(response.get(0).bookmarkedAt()).isEqualTo(LocalDateTime.of(2026, 6, 11, 11, 0));
    }

    @Test
    @DisplayName("Rejects bookmark without user")
    void toggleMessageBookmarkWithoutUser() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> bookmarkService.toggleMessageBookmark(1L, 100L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Rejects bookmark by non workspace member")
    void toggleMessageBookmarkByNonMember() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookmarkService.toggleMessageBookmark(1L, 100L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Rejects bookmark for message in different channel")
    void toggleMessageBookmarkForDifferentChannelMessage() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        Channel otherChannel = channel(2L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace, user("sender"));
        Thread message = message(100L, otherChannel, member, "hello");

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> bookmarkService.toggleMessageBookmark(1L, 100L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Rejects bookmark for non user message")
    void toggleMessageBookmarkForNonUserMessage() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(20L, workspace, user("sender"));
        Thread message = message(100L, channel, member, "system message");
        ReflectionTestUtils.setField(message, "threadType", "system");

        when(channelRepository.findById(1L)).thenReturn(Optional.of(channel));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> bookmarkService.toggleMessageBookmark(1L, 100L, 30L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
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

    private WorkspaceMember workspaceMember(Long id, Workspace workspace, User user) {
        WorkspaceMember member = WorkspaceMember.create(workspace, user, "editor");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Thread message(Long id, Channel channel, WorkspaceMember member, String content) {
        Thread thread = Thread.createChannelMessage(channel, member, content);
        ReflectionTestUtils.setField(thread, "id", id);
        return thread;
    }

    private User user(String name) {
        User user = mock(User.class);
        lenient().when(user.getDisplayName()).thenReturn(name);
        return user;
    }
}
