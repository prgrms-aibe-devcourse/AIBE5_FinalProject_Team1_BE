package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("활성 워크스페이스 멤버이면 채널 메시지를 저장한다")
    void createChannelMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(senderMemberId, "hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(entityManager.find(WorkspaceMember.class, senderMemberId)).thenReturn(sender);
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.senderMemberId()).isEqualTo(senderMemberId);
        assertThat(response.senderName()).isEqualTo("tester");
        assertThat(response.content()).isEqualTo("hello");
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("작성자가 비활성 멤버이면 채널 메시지를 저장하지 않는다")
    void createChannelMessageWithInactiveSender() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, false, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(senderMemberId, "hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(entityManager.find(WorkspaceMember.class, senderMemberId)).thenReturn(sender);

        assertThatThrownBy(() -> chatMessageService.createChannelMessage(channelId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("작성자가 채널의 워크스페이스 멤버가 아니면 채널 메시지를 저장하지 않는다")
    void createChannelMessageWithDifferentWorkspaceSender() {
        Long channelId = 1L;
        Long senderMemberId = 10L;
        Channel channel = channel(channelId, workspace(2L));
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace(3L), true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(senderMemberId, "hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(entityManager.find(WorkspaceMember.class, senderMemberId)).thenReturn(sender);

        assertThatThrownBy(() -> chatMessageService.createChannelMessage(channelId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("REST 저장 요청 사용자가 활성 워크스페이스 멤버이면 채널 메시지를 저장한다")
    void createChannelMessageByRest() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.senderMemberId()).isEqualTo(senderMemberId);
        assertThat(response.senderName()).isEqualTo("tester");
        assertThat(response.content()).isEqualTo("hello");
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("REST 저장 요청 사용자 식별값이 없으면 UNAUTHORIZED 예외가 발생한다")
    void createChannelMessageByRestWithoutUserId() {
        Long channelId = 1L;
        Channel channel = channel(channelId, workspace(2L));
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);

        assertThatThrownBy(() -> chatMessageService.createChannelMessage(channelId, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("REST 저장 요청 사용자가 채널 워크스페이스 멤버가 아니면 FORBIDDEN 예외가 발생한다")
    void createChannelMessageByRestWithForbiddenUser() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Channel channel = channel(channelId, workspace(workspaceId));
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.createChannelMessage(channelId, userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("채널 멤버이면 최신 메시지를 limit만큼 조회하고 생성 순서로 반환한다")
    void getChannelMessages() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Channel channel = channel(channelId, workspace(workspaceId));
        WorkspaceMember sender = workspaceMember(10L, user("tester", "테스터"));
        Thread older = thread(100L, channel, sender, "첫 번째 메시지", LocalDateTime.of(2026, 6, 8, 10, 0));
        Thread newer = thread(101L, channel, sender, "두 번째 메시지", LocalDateTime.of(2026, 6, 8, 10, 1));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(newer, older));

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId, userId, null, 30);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        assertThat(responses.get(0).channelId()).isEqualTo(channelId);
        assertThat(responses.get(0).senderMemberId()).isEqualTo(10L);
        assertThat(responses.get(0).senderName()).isEqualTo("테스터");
        assertThat(responses.get(0).content()).isEqualTo("첫 번째 메시지");
        assertThat(responses.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 8, 10, 0));
        assertThat(responses.get(1).id()).isEqualTo(101L);
        assertThat(responses.get(1).content()).isEqualTo("두 번째 메시지");

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(threadRepository).findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(30);
    }

    @Test
    @DisplayName("cursor가 있으면 cursor보다 이전 메시지를 조회한다")
    void getChannelMessagesWithCursor() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long cursor = 200L;
        Channel channel = channel(channelId, workspace(workspaceId));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(workspaceMember(10L, user("tester", "tester"))));
        when(threadRepository.findAllByChannel_IdAndThreadTypeAndIdLessThanOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.eq(cursor),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of());

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId, userId, cursor, 20);

        assertThat(responses).isEmpty();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(threadRepository).findAllByChannel_IdAndThreadTypeAndIdLessThanOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.eq(cursor),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("존재하지 않는 채널이면 CHANNEL_NOT_FOUND 예외가 발생한다")
    void getChannelMessagesWithMissingChannel() {
        Long channelId = 999L;
        Long userId = 3L;
        when(entityManager.find(Channel.class, channelId)).thenReturn(null);

        assertThatThrownBy(() -> chatMessageService.getChannelMessages(channelId, userId, null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(threadRepository, never()).findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 FORBIDDEN 예외가 발생한다")
    void getChannelMessagesWithForbiddenUser() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Channel channel = channel(channelId, workspace(workspaceId));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.getChannelMessages(channelId, userId, null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadRepository, never()).findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    @DisplayName("사용자 식별값이 없으면 UNAUTHORIZED 예외가 발생한다")
    void getChannelMessagesWithoutUserId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Channel channel = channel(channelId, workspace(workspaceId));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);

        assertThatThrownBy(() -> chatMessageService.getChannelMessages(channelId, null, null, 30))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(threadRepository, never()).findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        );
    }

    private static Thread thread(
            Long id,
            Channel channel,
            WorkspaceMember sender,
            String content,
            LocalDateTime createdAt
    ) {
        Thread thread = newInstance(Thread.class);
        ReflectionTestUtils.setField(thread, "id", id);
        ReflectionTestUtils.setField(thread, "channel", channel);
        ReflectionTestUtils.setField(thread, "createdBy", sender);
        ReflectionTestUtils.setField(thread, "threadType", Thread.TYPE_USER_MESSAGE);
        ReflectionTestUtils.setField(thread, "content", content);
        ReflectionTestUtils.setField(thread, "createdAt", createdAt);
        return thread;
    }

    private static Channel channel(Long id, Workspace workspace) {
        Channel channel = newInstance(Channel.class);
        ReflectionTestUtils.setField(channel, "id", id);
        ReflectionTestUtils.setField(channel, "workspace", workspace);
        return channel;
    }

    private static Workspace workspace(Long id) {
        Workspace workspace = newInstance(Workspace.class);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember workspaceMember(Long id, User user) {
        WorkspaceMember member = newInstance(WorkspaceMember.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "user", user);
        return member;
    }

    private static WorkspaceMember workspaceMember(Long id, Workspace workspace, boolean isActive, User user) {
        WorkspaceMember member = workspaceMember(id, user);
        ReflectionTestUtils.setField(member, "workspace", workspace);
        ReflectionTestUtils.setField(member, "isActive", isActive);
        return member;
    }

    private static User user(String username, String displayName) {
        User user = newInstance(User.class);
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
