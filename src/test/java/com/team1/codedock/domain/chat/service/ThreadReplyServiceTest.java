package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class ThreadReplyServiceTest {

    @Mock
    private ThreadReplyRepository threadReplyRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private MentionService mentionService;

    @InjectMocks
    private ThreadReplyService threadReplyService;

    @Test
    @DisplayName("스레드 답글 목록을 생성 순서로 조회한다")
    void getReplies() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "테스터"));
        ThreadReply first = reply(100L, thread, member, "첫 번째 답글", LocalDateTime.of(2026, 6, 9, 10, 0));
        ThreadReply second = reply(101L, thread, member, "두 번째 답글", LocalDateTime.of(2026, 6, 9, 10, 1));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findAllByThread_IdOrderByCreatedAtAscIdAsc(threadId))
                .thenReturn(List.of(first, second));

        List<ThreadReplyResponse> responses = threadReplyService.getReplies(threadId, userId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        assertThat(responses.get(0).threadId()).isEqualTo(threadId);
        assertThat(responses.get(0).senderMemberId()).isEqualTo(20L);
        assertThat(responses.get(0).senderName()).isEqualTo("테스터");
        assertThat(responses.get(0).content()).isEqualTo("첫 번째 답글");
        assertThat(responses.get(1).id()).isEqualTo(101L);
        assertThat(responses.get(1).content()).isEqualTo("두 번째 답글");
    }

    @Test
    @DisplayName("활성 워크스페이스 멤버이면 스레드 답글을 저장한다")
    void createReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "테스터"));
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("새 답글");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        ThreadReplyResponse response = threadReplyService.createReply(threadId, userId, request);

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.threadId()).isEqualTo(threadId);
        assertThat(response.senderMemberId()).isEqualTo(20L);
        assertThat(response.senderName()).isEqualTo("테스터");
        assertThat(response.content()).isEqualTo("새 답글");
        verify(threadReplyRepository).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
        verify(mentionService).createMentionsForThreadReply(
                org.mockito.ArgumentMatchers.any(ThreadReply.class),
                org.mockito.ArgumentMatchers.eq(member),
                org.mockito.ArgumentMatchers.eq("새 답글")
        );
    }

    @Test
    @DisplayName("존재하지 않는 스레드이면 THREAD_NOT_FOUND 예외가 발생한다")
    void getRepliesWithMissingThread() {
        Long threadId = 999L;
        Long userId = 3L;
        when(entityManager.find(Thread.class, threadId)).thenReturn(null);

        assertThatThrownBy(() -> threadReplyService.getReplies(threadId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THREAD_NOT_FOUND);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    @DisplayName("사용자 식별값이 없으면 UNAUTHORIZED 예외가 발생한다")
    void createReplyWithoutUserId() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);

        assertThatThrownBy(() -> threadReplyService.createReply(threadId, null, new ThreadReplyCreateRequest("답글")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(threadReplyRepository, never()).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 FORBIDDEN 예외가 발생한다")
    void createReplyWithForbiddenUser() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("답글")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadReplyRepository, never()).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("빈 답글 내용이면 INVALID_INPUT 예외가 발생한다")
    void createReplyWithBlankContent() {
        assertThatThrownBy(() -> threadReplyService.createReply(1L, 3L, new ThreadReplyCreateRequest(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(entityManager, never()).find(org.mockito.ArgumentMatchers.eq(Thread.class), org.mockito.ArgumentMatchers.any());
    }

    private static ThreadReply reply(
            Long id,
            Thread thread,
            WorkspaceMember member,
            String content,
            LocalDateTime createdAt
    ) {
        ThreadReply reply = ThreadReply.create(thread, member, content);
        ReflectionTestUtils.setField(reply, "id", id);
        ReflectionTestUtils.setField(reply, "createdAt", createdAt);
        return reply;
    }

    private static Thread thread(Long id, Channel channel) {
        Thread thread = newInstance(Thread.class);
        ReflectionTestUtils.setField(thread, "id", id);
        ReflectionTestUtils.setField(thread, "channel", channel);
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
