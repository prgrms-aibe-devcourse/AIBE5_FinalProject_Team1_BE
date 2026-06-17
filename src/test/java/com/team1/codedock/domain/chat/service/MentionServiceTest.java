package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;
import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.entity.Mention;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.MentionRepository;
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

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private ChatNotificationService chatNotificationService;

    @InjectMocks
    private MentionService mentionService;

    @Test
    @DisplayName("채널 메시지에서 워크스페이스 멤버 멘션을 저장하고 개인 알림을 보낸다")
    void createMentionsForThread() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        WorkspaceMember alice = workspaceMember(21L, workspace, user("alice", "Alice"));
        WorkspaceMember bob = workspaceMember(22L, workspace, user("bob", "Bob"));
        Thread thread = thread(100L, channel, sender, "hello @alice @bob @alice @none");

        when(workspaceMemberRepository.findActiveMentionTargets(10L, List.of("alice", "bob", "none")))
                .thenReturn(List.of(alice, bob));

        mentionService.createMentionsForThread(thread, sender, thread.getContent());

        ArgumentCaptor<Iterable<Mention>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(mentionRepository).saveAll(captor.capture());
        List<Mention> savedMentions = toList(captor.getValue());

        assertThat(savedMentions).hasSize(2);
        assertThat(savedMentions.get(0).getWorkspace()).isEqualTo(workspace);
        assertThat(savedMentions.get(0).getThread()).isEqualTo(thread);
        assertThat(savedMentions.get(0).getThreadReply()).isNull();
        assertThat(savedMentions.get(0).getMentionedMember()).isEqualTo(alice);
        assertThat(savedMentions.get(0).getMentionedByMember()).isEqualTo(sender);
        assertThat(savedMentions.get(0).isRead()).isFalse();
        assertThat(savedMentions.get(1).getMentionedMember()).isEqualTo(bob);

        ArgumentCaptor<String> userKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ChatNotificationResponse> notificationCaptor =
                ArgumentCaptor.forClass(ChatNotificationResponse.class);
        verify(chatNotificationService, times(2))
                .sendNotification(userKeyCaptor.capture(), notificationCaptor.capture());

        assertThat(userKeyCaptor.getAllValues()).containsExactly("alice@test.com", "bob@test.com");
        assertThat(notificationCaptor.getAllValues())
                .extracting(ChatNotificationResponse::mentionedMemberId)
                .containsExactly(21L, 22L);
        assertThat(notificationCaptor.getAllValues())
                .allSatisfy(notification -> {
                    assertThat(notification.workspaceId()).isEqualTo(10L);
                    assertThat(notification.channelId()).isEqualTo(1L);
                    assertThat(notification.threadId()).isEqualTo(100L);
                    assertThat(notification.threadReplyId()).isNull();
                    assertThat(notification.message()).isEqualTo("새 멘션이 도착했습니다.");
                });
    }

    @Test
    @DisplayName("한글 멘션과 점/하이픈이 포함된 기존 멘션 형식을 함께 파싱한다")
    void createMentionsForThreadWithKoreanMentionNames() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        WorkspaceMember koreanMember = workspaceMember(21L, workspace, user("kim", "김재준"));
        WorkspaceMember dottedMember = workspaceMember(22L, workspace, user("user.name", "User Name"));
        WorkspaceMember hyphenMember = workspaceMember(23L, workspace, user("dev-1", "Dev One"));
        Thread thread = thread(
                100L,
                channel,
                sender,
                "확인 부탁드립니다 @김재준 @user.name @dev-1 @김재준"
        );

        when(workspaceMemberRepository.findActiveMentionTargets(
                10L,
                List.of("김재준", "user.name", "dev-1")
        )).thenReturn(List.of(koreanMember, dottedMember, hyphenMember));

        mentionService.createMentionsForThread(thread, sender, thread.getContent());

        ArgumentCaptor<Iterable<Mention>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(mentionRepository).saveAll(captor.capture());
        List<Mention> savedMentions = toList(captor.getValue());

        assertThat(savedMentions).hasSize(3);
        assertThat(savedMentions)
                .extracting(Mention::getMentionedMember)
                .containsExactly(koreanMember, dottedMember, hyphenMember);

        ArgumentCaptor<String> userKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatNotificationService, times(3))
                .sendNotification(userKeyCaptor.capture(), any());
        assertThat(userKeyCaptor.getAllValues())
                .containsExactly("kim@test.com", "user.name@test.com", "dev-1@test.com");
    }

    @Test
    @DisplayName("멘션 토큰이 없으면 조회와 저장을 생략한다")
    void createMentionsForThreadWithoutMentionToken() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        Thread thread = thread(100L, channel, sender, "hello team");

        mentionService.createMentionsForThread(thread, sender, thread.getContent());

        verify(workspaceMemberRepository, never()).findActiveMentionTargets(anyLong(), anyList());
        verify(mentionRepository, never()).saveAll(any());
        verify(chatNotificationService, never()).sendNotification(anyString(), any());
    }

    @Test
    @DisplayName("답글 멘션을 저장하고 답글 개인 알림을 보낸다")
    void createMentionsForThreadReply() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        WorkspaceMember alice = workspaceMember(21L, workspace, user("alice", "Alice"));
        Thread thread = thread(100L, channel, sender, "message");
        ThreadReply reply = reply(200L, thread, sender, "reply to @alice");

        when(workspaceMemberRepository.findActiveMentionTargets(10L, List.of("alice")))
                .thenReturn(List.of(alice));

        mentionService.createMentionsForThreadReply(reply, sender, reply.getContent());

        ArgumentCaptor<Iterable<Mention>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(mentionRepository).saveAll(captor.capture());
        List<Mention> savedMentions = toList(captor.getValue());

        assertThat(savedMentions).hasSize(1);
        assertThat(savedMentions.get(0).getThread()).isNull();
        assertThat(savedMentions.get(0).getThreadReply()).isEqualTo(reply);
        assertThat(savedMentions.get(0).getMentionedMember()).isEqualTo(alice);

        ArgumentCaptor<ChatNotificationResponse> notificationCaptor =
                ArgumentCaptor.forClass(ChatNotificationResponse.class);
        verify(chatNotificationService)
                .sendNotification(eq("alice@test.com"), notificationCaptor.capture());

        ChatNotificationResponse notification = notificationCaptor.getValue();
        assertThat(notification.workspaceId()).isEqualTo(10L);
        assertThat(notification.channelId()).isEqualTo(1L);
        assertThat(notification.threadId()).isEqualTo(100L);
        assertThat(notification.threadReplyId()).isEqualTo(200L);
        assertThat(notification.mentionedMemberId()).isEqualTo(21L);
        assertThat(notification.message()).isEqualTo("새 멘션 답글이 도착했습니다.");
    }

    @Test
    @DisplayName("내 워크스페이스 멘션 목록을 조회한다")
    void getMyMentions() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        WorkspaceMember mentioned = workspaceMember(21L, workspace, user("alice", "Alice"));
        Thread thread = thread(100L, channel, sender, "hello @alice");
        Mention mention = Mention.createForThread(workspace, thread, mentioned, sender);
        ReflectionTestUtils.setField(mention, "id", 300L);
        ReflectionTestUtils.setField(mention, "createdAt", LocalDateTime.of(2026, 6, 11, 12, 0));

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(mentioned));
        when(mentionRepository.findAllByWorkspace_IdAndMentionedMember_IdOrderByCreatedAtDesc(10L, 21L))
                .thenReturn(List.of(mention));

        List<MentionResponse> response = mentionService.getMyMentions(10L, 30L);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(300L);
        assertThat(response.get(0).channelId()).isEqualTo(1L);
        assertThat(response.get(0).threadId()).isEqualTo(100L);
        assertThat(response.get(0).threadReplyId()).isNull();
        assertThat(response.get(0).mentionedByName()).isEqualTo("Sender");
        assertThat(response.get(0).read()).isFalse();
    }

    @Test
    @DisplayName("내 멘션을 읽음 처리한다")
    void markMentionAsRead() {
        Workspace workspace = workspace(10L);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(20L, workspace, user("sender", "Sender"));
        WorkspaceMember mentioned = workspaceMember(21L, workspace, user("alice", "Alice"));
        Thread thread = thread(100L, channel, sender, "hello @alice");
        Mention mention = Mention.createForThread(workspace, thread, mentioned, sender);
        ReflectionTestUtils.setField(mention, "id", 300L);

        when(mentionRepository.findById(300L)).thenReturn(Optional.of(mention));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 30L))
                .thenReturn(Optional.of(mentioned));
        when(mentionRepository.findByIdAndMentionedMember_Id(300L, 21L))
                .thenReturn(Optional.of(mention));

        MentionResponse response = mentionService.markMentionAsRead(300L, 30L);

        assertThat(response.read()).isTrue();
        assertThat(mention.isRead()).isTrue();
    }

    @Test
    @DisplayName("사용자 없이 멘션 목록을 조회하면 거부한다")
    void getMyMentionsWithoutUser() {
        assertThatThrownBy(() -> mentionService.getMyMentions(10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private static List<Mention> toList(Iterable<Mention> mentions) {
        List<Mention> result = new ArrayList<>();
        mentions.forEach(result::add);
        return result;
    }

    private static ThreadReply reply(Long id, Thread thread, WorkspaceMember member, String content) {
        ThreadReply reply = ThreadReply.create(thread, member, content);
        ReflectionTestUtils.setField(reply, "id", id);
        return reply;
    }

    private static Thread thread(Long id, Channel channel, WorkspaceMember sender, String content) {
        Thread thread = Thread.createChannelMessage(channel, sender, content);
        ReflectionTestUtils.setField(thread, "id", id);
        return thread;
    }

    private static Channel channel(Long id, Workspace workspace) {
        Channel channel = Channel.createCustom(workspace, "team-chat", null);
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private static Workspace workspace(Long id) {
        Workspace workspace = newInstance(Workspace.class);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember workspaceMember(Long id, Workspace workspace, User user) {
        WorkspaceMember member = WorkspaceMember.create(workspace, user, "editor");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static User user(String username, String displayName) {
        User user = newInstance(User.class);
        ReflectionTestUtils.setField(user, "email", username + "@test.com");
        ReflectionTestUtils.setField(user, "username", username);
        ReflectionTestUtils.setField(user, "displayName", displayName);
        return user;
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create test entity: " + type.getSimpleName(), e);
        }
    }
}
