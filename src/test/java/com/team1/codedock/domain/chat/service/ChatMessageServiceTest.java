package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
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
    private ChannelRepository channelRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("채널 메시지 목록을 생성 순서대로 조회한다")
    void getChannelMessages() {
        Long channelId = 1L;
        Channel channel = channel(channelId);
        WorkspaceMember sender = workspaceMember(10L, user("tester", "테스터"));
        Thread first = thread(100L, channel, sender, "첫 번째 메시지", LocalDateTime.of(2026, 6, 8, 10, 0));
        Thread second = thread(101L, channel, sender, "두 번째 메시지", LocalDateTime.of(2026, 6, 8, 10, 1));

        when(channelRepository.existsById(channelId)).thenReturn(true);
        when(threadRepository.findAllByChannel_IdAndThreadTypeOrderByCreatedAtAscIdAsc(
                channelId,
                Thread.TYPE_USER_MESSAGE
        )).thenReturn(List.of(first, second));

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        assertThat(responses.get(0).channelId()).isEqualTo(channelId);
        assertThat(responses.get(0).senderMemberId()).isEqualTo(10L);
        assertThat(responses.get(0).senderName()).isEqualTo("테스터");
        assertThat(responses.get(0).content()).isEqualTo("첫 번째 메시지");
        assertThat(responses.get(0).createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 8, 10, 0));
        assertThat(responses.get(1).id()).isEqualTo(101L);
        assertThat(responses.get(1).content()).isEqualTo("두 번째 메시지");

        verify(threadRepository).findAllByChannel_IdAndThreadTypeOrderByCreatedAtAscIdAsc(
                channelId,
                Thread.TYPE_USER_MESSAGE
        );
    }

    @Test
    @DisplayName("존재하지 않는 채널이면 CHANNEL_NOT_FOUND 예외가 발생한다")
    void getChannelMessagesWithMissingChannel() {
        Long channelId = 999L;
        when(channelRepository.existsById(channelId)).thenReturn(false);

        assertThatThrownBy(() -> chatMessageService.getChannelMessages(channelId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);

        verify(threadRepository, never()).findAllByChannel_IdAndThreadTypeOrderByCreatedAtAscIdAsc(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
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

    private static Channel channel(Long id) {
        Channel channel = newInstance(Channel.class);
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
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
