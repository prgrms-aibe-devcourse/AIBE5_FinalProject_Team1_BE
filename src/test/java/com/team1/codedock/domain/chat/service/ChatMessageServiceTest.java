package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageUpdateRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.dto.ThreadTypingEventResponse;
import com.team1.codedock.domain.chat.dto.TypingEventRequest;
import com.team1.codedock.domain.chat.dto.TypingEventResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
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

    @Mock
    private MentionService mentionService;

    @Mock
    private ThreadAttachmentService threadAttachmentService;

    @Mock
    private WorkspaceEventService workspaceEventService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("활성 워크스페이스 멤버이면 채널 메시지를 저장한다")
    void createChannelMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        User senderUser = user("tester", "tester");
        ReflectionTestUtils.setField(senderUser, "avatarUrl", "https://example.com/message-sender.png");
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, senderUser);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

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
        assertThat(response.senderAvatarUrl()).isEqualTo("https://example.com/message-sender.png");
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.isDeleted()).isFalse();
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
        verify(mentionService).createMentionsForThread(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(sender),
                org.mockito.ArgumentMatchers.eq("hello")
        );
    }

    @Test
    @DisplayName("채널 메시지 이모지는 인코딩해 저장하고 응답과 멘션 입력은 원문을 유지한다")
    void createChannelMessageEncodesEmojiContent() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        String content = "배포 완료 👍🔥";
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(content);

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

        assertThat(response.content()).isEqualTo(content);
        ArgumentCaptor<Thread> threadCaptor = ArgumentCaptor.forClass(Thread.class);
        verify(threadRepository).save(threadCaptor.capture());
        String storedContent = threadCaptor.getValue().getContent();
        assertThat(storedContent).isNotEqualTo(content);
        assertThat(storedContent).isEqualTo("배포 완료 [[emoji:like]][[emoji:fire]]");
        assertThat(storedContent).doesNotContain("👍", "🔥");
        assertThat(ChatContentEmojiCodec.decode(storedContent)).isEqualTo(content);
        verify(mentionService).createMentionsForThread(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(sender),
                org.mockito.ArgumentMatchers.eq(content)
        );
    }

    @Test
    @DisplayName("REST message create saves attachments and returns them")
    void createChannelMessageByRestWithAttachments() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        ThreadAttachmentRequest attachmentRequest = new ThreadAttachmentRequest(
                "image",
                null,
                "https://example.com/image.png",
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L
        );
        String content = "첨부 확인 👍📝";
        ChannelMessageRestCreateRequest request =
                new ChannelMessageRestCreateRequest(content, List.of(attachmentRequest));
        ThreadAttachmentResponse attachmentResponse = new ThreadAttachmentResponse(
                1L,
                "image",
                null,
                "https://example.com/image.png",
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });
        when(threadAttachmentService.saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(List.of(attachmentRequest))
        )).thenReturn(List.of(attachmentResponse));

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo(content);
        assertThat(response.attachments()).containsExactly(attachmentResponse);
        ArgumentCaptor<Thread> threadCaptor = ArgumentCaptor.forClass(Thread.class);
        verify(threadRepository).save(threadCaptor.capture());
        assertThat(threadCaptor.getValue().getContent()).isEqualTo("첨부 확인 [[emoji:like]][[emoji:memo]]");
        verify(threadAttachmentService).saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(List.of(attachmentRequest))
        );
    }

    @Test
    @DisplayName("WebSocket 메시지 작성 사용자가 활성 채널 멤버가 아니면 저장하지 않는다")
    void createChannelMessageWithInactiveSender() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

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
    @DisplayName("WebSocket 메시지 작성 사용자 식별값이 없으면 UNAUTHORIZED 예외가 발생한다")
    void createChannelMessageWithoutUserId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Channel channel = channel(channelId, workspace(workspaceId));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);

        assertThatThrownBy(() -> chatMessageService.createChannelMessage(channelId, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("typing 이벤트는 인증 사용자 기준으로 워크스페이스 멤버 id를 채운다")
    void createTypingEventResponse() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long workspaceMemberId = 10L;
        Channel channel = channel(channelId, workspace(workspaceId));
        WorkspaceMember member = workspaceMember(workspaceMemberId, channel.getWorkspace(), true, user("tester", "tester"));
        TypingEventRequest request = new TypingEventRequest(true);

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));

        TypingEventResponse response = chatMessageService.createTypingEventResponse(channelId, userId, request);

        assertThat(response.channelId()).isEqualTo(channelId);
        assertThat(response.workspaceMemberId()).isEqualTo(workspaceMemberId);
        assertThat(response.senderName()).isEqualTo("tester");
        assertThat(response.typing()).isTrue();
    }

    @Test
    @DisplayName("스레드 typing 이벤트는 인증 사용자 기준으로 스레드 멤버 정보를 채운다")
    void createThreadTypingEventResponse() {
        Long threadId = 100L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(1L, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true, user("tester", "테스터"));
        Thread thread = thread(threadId, channel, member, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        TypingEventRequest request = new TypingEventRequest(true);

        when(threadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));

        ThreadTypingEventResponse response = chatMessageService.createThreadTypingEventResponse(threadId, userId, request);

        assertThat(response.threadId()).isEqualTo(threadId);
        assertThat(response.workspaceMemberId()).isEqualTo(10L);
        assertThat(response.senderName()).isEqualTo("테스터");
        assertThat(response.typing()).isTrue();
    }

    @Test
    @DisplayName("스레드 typing 이벤트는 존재하지 않는 스레드를 거부한다")
    void createThreadTypingEventResponseWithMissingThread() {
        when(threadRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.createThreadTypingEventResponse(
                100L,
                3L,
                new TypingEventRequest(true)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    @DisplayName("스레드 typing 이벤트는 비소속 사용자를 거부한다")
    void createThreadTypingEventResponseWithForbiddenUser() {
        Long threadId = 100L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(1L, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "테스터"));
        Thread thread = thread(threadId, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(threadRepository.findById(threadId)).thenReturn(Optional.of(thread));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatMessageService.createThreadTypingEventResponse(
                threadId,
                userId,
                new TypingEventRequest(true)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
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
        verify(mentionService).createMentionsForThread(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(sender),
                org.mockito.ArgumentMatchers.eq("hello")
        );
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

    @Test
    @DisplayName("Message author can update channel message")
    void updateChannelMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long memberId = 10L;
        Long messageId = 100L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(memberId, workspace, true, user("tester", "tester"));
        Thread message = thread(messageId, channel, member, "before", LocalDateTime.of(2026, 6, 9, 10, 0));
        ChannelMessageUpdateRequest request = new ChannelMessageUpdateRequest("after");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(messageId)).thenReturn(Optional.of(message));

        ChannelMessageResponse response = chatMessageService.updateChannelMessage(channelId, messageId, userId, request);

        assertThat(response.id()).isEqualTo(messageId);
        assertThat(response.content()).isEqualTo("after");
        assertThat(message.getContent()).isEqualTo("after");
    }

    @Test
    @DisplayName("메시지 수정 이모지는 인코딩해 저장하고 응답은 원문을 반환한다")
    void updateChannelMessageEncodesEmojiContent() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long memberId = 10L;
        Long messageId = 100L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(memberId, workspace, true, user("tester", "tester"));
        Thread message = thread(messageId, channel, member, "before", LocalDateTime.of(2026, 6, 9, 10, 0));
        String content = "수정 완료 😄";
        ChannelMessageUpdateRequest request = new ChannelMessageUpdateRequest(content);

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(messageId)).thenReturn(Optional.of(message));

        ChannelMessageResponse response = chatMessageService.updateChannelMessage(channelId, messageId, userId, request);

        assertThat(response.id()).isEqualTo(messageId);
        assertThat(response.content()).isEqualTo(content);
        assertThat(message.getContent()).isNotEqualTo(content);
        assertThat(message.getContent()).isEqualTo("수정 완료 [[emoji:smile]]");
        assertThat(message.getContent()).doesNotContain("😄");
        assertThat(ChatContentEmojiCodec.decode(message.getContent())).isEqualTo(content);
    }

    @Test
    @DisplayName("Non-author cannot update channel message")
    void updateChannelMessageWithDifferentAuthor() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember requester = workspaceMember(10L, workspace, true, user("requester", "requester"));
        WorkspaceMember author = workspaceMember(20L, workspace, true, user("author", "author"));
        Thread message = thread(100L, channel, author, "before", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(requester));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatMessageService.updateChannelMessage(
                channelId,
                100L,
                userId,
                new ChannelMessageUpdateRequest("after")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Cannot update message from another channel")
    void updateChannelMessageWithDifferentChannel() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel requestedChannel = channel(channelId, workspace);
        Channel actualChannel = channel(99L, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true, user("tester", "tester"));
        Thread message = thread(100L, actualChannel, member, "before", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Channel.class, channelId)).thenReturn(requestedChannel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatMessageService.updateChannelMessage(
                channelId,
                100L,
                userId,
                new ChannelMessageUpdateRequest("after")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Cannot update non-user message")
    void updateChannelMessageWithNonUserMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true, user("tester", "tester"));
        Thread message = thread(100L, channel, member, "system message", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(message, "threadType", "system");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatMessageService.updateChannelMessage(
                channelId,
                100L,
                userId,
                new ChannelMessageUpdateRequest("after")
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Message author can soft delete channel message")
    void deleteChannelMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long memberId = 10L;
        Long messageId = 100L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(memberId, workspace, true, user("tester", "tester"));
        Thread message = thread(messageId, channel, member, "before", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(messageId)).thenReturn(Optional.of(message));

        ChannelMessageResponse response = chatMessageService.deleteChannelMessage(channelId, messageId, userId);

        assertThat(response.id()).isEqualTo(messageId);
        assertThat(response.content()).isEqualTo(Thread.DELETED_MESSAGE_CONTENT);
        assertThat(response.attachments()).isEmpty();
        assertThat(message.getContent()).isEqualTo(Thread.DELETED_MESSAGE_CONTENT);
        verify(threadRepository, never()).delete(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("Non-author cannot delete channel message")
    void deleteChannelMessageWithDifferentAuthor() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember requester = workspaceMember(10L, workspace, true, user("requester", "requester"));
        WorkspaceMember author = workspaceMember(20L, workspace, true, user("author", "author"));
        Thread message = thread(100L, channel, author, "before", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(requester));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatMessageService.deleteChannelMessage(channelId, 100L, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Cannot delete non-user message")
    void deleteChannelMessageWithNonUserMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true, user("tester", "tester"));
        Thread message = thread(100L, channel, member, "system message", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(message, "threadType", "system");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> chatMessageService.deleteChannelMessage(channelId, 100L, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("인코딩 저장된 메시지는 목록 조회 응답에서 원문 이모지로 복원된다")
    void getChannelMessagesDecodesEmojiContent() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Channel channel = channel(channelId, workspace(workspaceId));
        WorkspaceMember sender = workspaceMember(10L, user("tester", "테스터"));
        String content = "목록 조회 👍";
        Thread message = thread(
                100L,
                channel,
                sender,
                ChatContentEmojiCodec.encode(content),
                LocalDateTime.of(2026, 6, 8, 10, 0)
        );

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(message));

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId, userId, null, 30);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo(content);
    }

    @Test
    @DisplayName("Channel message list includes attachments")
    void getChannelMessagesWithAttachments() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        Thread message = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ThreadAttachmentResponse attachment = new ThreadAttachmentResponse(
                1L,
                "link",
                null,
                "https://example.com",
                "example",
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(message));
        when(threadAttachmentService.getAttachmentMap(List.of(100L)))
                .thenReturn(java.util.Map.of(100L, List.of(attachment)));

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId, userId, null, 30);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).attachments()).containsExactly(attachment);
    }

    @Test
    @DisplayName("Deleted channel message list response does not include attachments")
    void getChannelMessagesExcludesAttachmentsForDeletedMessage() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        Thread message = thread(100L, channel, sender, Thread.DELETED_MESSAGE_CONTENT, LocalDateTime.of(2026, 6, 9, 10, 0));
        ThreadAttachmentResponse attachment = new ThreadAttachmentResponse(
                1L,
                "link",
                null,
                "https://example.com",
                "example",
                null,
                null,
                null,
                null,
                null,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findAllByChannel_IdAndThreadTypeOrderByIdDesc(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(Thread.TYPE_USER_MESSAGE),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(message));
        when(threadAttachmentService.getAttachmentMap(List.of(100L)))
                .thenReturn(java.util.Map.of(100L, List.of(attachment)));

        List<ChannelMessageResponse> responses = chatMessageService.getChannelMessages(channelId, userId, null, 30);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo(Thread.DELETED_MESSAGE_CONTENT);
        assertThat(responses.get(0).attachments()).isEmpty();
    }

    // ---------------------------------------------------------------------
    // clientMessageId 기반 멱등성 (issue #198)
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("WS: clientMessageId가 있으면 Thread에 저장하고 응답에 그대로 echo한다")
    void createChannelMessageStoresAndEchoesClientMessageId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", null, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.empty());
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.clientMessageId()).isEqualTo("cmid-1");
        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        verify(threadRepository).save(captor.capture());
        assertThat(captor.getValue().getClientMessageId()).isEqualTo("cmid-1");
    }

    @Test
    @DisplayName("WS: 같은 clientMessageId가 이미 있으면 새로 저장하지 않고 기존 메시지를 반환한다(멱등)")
    void createChannelMessageIsIdempotentForSameClientMessageId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        Thread existing = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(existing, "clientMessageId", "cmid-1");
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", null, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.of(existing));
        when(threadAttachmentService.getAttachments(100L)).thenReturn(List.of());

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.clientMessageId()).isEqualTo("cmid-1");
        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
        verify(mentionService, never()).createMentionsForThread(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.any(WorkspaceMember.class),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    @Test
    @DisplayName("WS: clientMessageId가 없으면 멱등 조회 없이 바로 저장한다")
    void createChannelMessageWithoutClientMessageIdSkipsLookup() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

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

        assertThat(response.clientMessageId()).isNull();
        verify(threadRepository, never()).findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("WS: clientMessageId가 공백 문자열이면 멱등 조회를 건너뛴다")
    void createChannelMessageWithBlankClientMessageIdSkipsLookup() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", null, "   ");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(threadRepository, never()).findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("REST: clientMessageId가 있으면 Thread에 저장하고 응답에 echo한다")
    void createChannelMessageByRestStoresClientMessageId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        ChannelMessageRestCreateRequest request =
                new ChannelMessageRestCreateRequest("hello", List.of(), null, "cmid-rest");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-rest"))
                .thenReturn(Optional.empty());
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });
        when(threadAttachmentService.saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(List.of())
        )).thenReturn(List.of());

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.clientMessageId()).isEqualTo("cmid-rest");
        ArgumentCaptor<Thread> captor = ArgumentCaptor.forClass(Thread.class);
        verify(threadRepository).save(captor.capture());
        assertThat(captor.getValue().getClientMessageId()).isEqualTo("cmid-rest");
    }

    @Test
    @DisplayName("REST: 같은 clientMessageId면 새로 저장하지 않고 기존 메시지(첨부 포함)를 반환한다")
    void createChannelMessageByRestIsIdempotent() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        Thread existing = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(existing, "clientMessageId", "cmid-rest");
        ThreadAttachmentResponse attachment = new ThreadAttachmentResponse(
                1L, "link", null, "https://example.com", "example",
                null, null, null, null, null, LocalDateTime.of(2026, 6, 9, 10, 0)
        );
        ChannelMessageRestCreateRequest request =
                new ChannelMessageRestCreateRequest("hello", List.of(), null, "cmid-rest");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-rest"))
                .thenReturn(Optional.of(existing));
        when(threadAttachmentService.getAttachments(100L)).thenReturn(List.of(attachment));

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.clientMessageId()).isEqualTo("cmid-rest");
        assertThat(response.attachments()).containsExactly(attachment);
        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
        verify(threadAttachmentService, never()).saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    @DisplayName("멱등 조회는 (channelId, senderMemberId, clientMessageId)로 수행한다")
    void idempotentLookupUsesChannelSenderAndClientMessageId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", null, "cmid-key");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-key"))
                .thenReturn(Optional.empty());
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(threadRepository).findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-key");
    }

    @Test
    @DisplayName("Thread.createChannelMessage는 clientMessageId를 저장하고, 기존 오버로드는 null로 둔다")
    void threadFactoryStoresClientMessageId() {
        Channel channel = channel(1L, workspace(2L));
        WorkspaceMember sender = workspaceMember(10L, channel.getWorkspace(), true, user("tester", "tester"));

        assertThat(Thread.createChannelMessage(channel, sender, "hi", null, "cmid-1").getClientMessageId())
                .isEqualTo("cmid-1");
        assertThat(Thread.createChannelMessage(channel, sender, "hi", null).getClientMessageId())
                .isNull();
        assertThat(Thread.createChannelMessage(channel, sender, "hi").getClientMessageId())
                .isNull();
    }

    @Test
    @DisplayName("ChannelMessageResponse.from은 Thread의 clientMessageId를 echo하고, 봇 메시지는 null이다")
    void responseEchoesClientMessageId() {
        Channel channel = channel(1L, workspace(2L));
        WorkspaceMember sender = workspaceMember(10L, channel.getWorkspace(), true, user("tester", "테스터"));
        Thread thread = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(thread, "clientMessageId", "cmid-xyz");

        assertThat(ChannelMessageResponse.from(thread).clientMessageId()).isEqualTo("cmid-xyz");
        assertThat(ChannelMessageResponse.fromBot(thread, "GitHub Bot", List.of()).clientMessageId()).isNull();
    }

    @Test
    @DisplayName("REST: clientMessageId가 없으면 멱등 조회 없이 저장한다")
    void createChannelMessageByRestWithoutClientMessageIdSkipsLookup() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(10L, workspace, true, user("tester", "tester"));
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
        when(threadAttachmentService.saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(List.of())
        )).thenReturn(List.of());

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(threadRepository, never()).findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verify(threadRepository).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("WS: 기존 메시지가 삭제 상태여도 멱등 응답은 삭제 내용을 반환하고 첨부는 조회하지 않는다")
    void idempotentHitReturnsDeletedExistingWithoutAttachments() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        Thread existing = thread(100L, channel, sender, Thread.DELETED_MESSAGE_CONTENT, LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(existing, "clientMessageId", "cmid-1");
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", null, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.of(existing));

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.clientMessageId()).isEqualTo("cmid-1");
        assertThat(response.content()).isEqualTo(Thread.DELETED_MESSAGE_CONTENT);
        assertThat(response.isDeleted()).isTrue();
        assertThat(response.attachments()).isEmpty();
        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
        verify(threadAttachmentService, never()).getAttachments(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("REST: 멱등 히트면 재전송된 첨부를 저장하지 않고 기존 첨부를 반환한다")
    void restIdempotentIgnoresResentAttachments() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        Thread existing = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(existing, "clientMessageId", "cmid-1");
        ThreadAttachmentRequest resentAttachment = new ThreadAttachmentRequest(
                "image", null, "https://example.com/new.png", "new.png", null, null, null, "image/png", 100L
        );
        ThreadAttachmentResponse existingAttachment = new ThreadAttachmentResponse(
                1L, "image", null, "https://example.com/original.png", "original.png",
                null, null, null, "image/png", 100L, LocalDateTime.of(2026, 6, 9, 10, 0)
        );
        ChannelMessageRestCreateRequest request =
                new ChannelMessageRestCreateRequest("hello", List.of(resentAttachment), null, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.of(existing));
        when(threadAttachmentService.getAttachments(100L)).thenReturn(List.of(existingAttachment));

        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        assertThat(response.attachments()).containsExactly(existingAttachment);
        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
        verify(threadAttachmentService, never()).saveAttachments(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    @DisplayName("WS: 멱등 히트면 replyTo 해석(findById)을 수행하지 않는다")
    void idempotentHitSkipsReplyToResolution() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        Thread existing = thread(100L, channel, sender, "hello", LocalDateTime.of(2026, 6, 9, 10, 0));
        ReflectionTestUtils.setField(existing, "clientMessageId", "cmid-1");
        // 재전송이 replyToMessageId를 포함해도 멱등 히트면 reply 해석을 건너뜀
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello", 55L, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.of(existing));
        when(threadAttachmentService.getAttachments(100L)).thenReturn(List.of());

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(threadRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verify(threadRepository, never()).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("WS: 새 메시지(멱등 미히트)는 clientMessageId가 있어도 멘션을 생성한다")
    void freshSaveWithClientMessageIdStillCreatesMentions() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));
        String content = "@tester 확인 부탁해요";
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(content, null, "cmid-1");

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(channelId, senderMemberId, "cmid-1"))
                .thenReturn(Optional.empty());
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(mentionService).createMentionsForThread(
                org.mockito.ArgumentMatchers.any(Thread.class),
                org.mockito.ArgumentMatchers.eq(sender),
                org.mockito.ArgumentMatchers.eq(content)
        );
    }

    @Test
    @DisplayName("WS: 다른 clientMessageId는 멱등 대상이 아니라 각각 저장된다")
    void differentClientMessageIdsAreNotDeduplicated() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, user("tester", "tester"));

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId(
                org.mockito.ArgumentMatchers.eq(channelId),
                org.mockito.ArgumentMatchers.eq(senderMemberId),
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(Optional.empty());
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 0));
            return saved;
        });

        chatMessageService.createChannelMessage(channelId, userId, new ChannelMessageCreateRequest("hello", null, "cmid-1"));
        chatMessageService.createChannelMessage(channelId, userId, new ChannelMessageCreateRequest("hello", null, "cmid-2"));

        verify(threadRepository, org.mockito.Mockito.times(2)).save(org.mockito.ArgumentMatchers.any(Thread.class));
    }

    @Test
    @DisplayName("replyTo가 있으면 원본 메시지 작성자에게 REPLY 이벤트를 기록한다")
    void createChannelMessageRecordsReplyEvent() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Long senderMemberId = 10L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        User senderUser = user("sender", "Sender");
        WorkspaceMember sender = workspaceMember(senderMemberId, workspace, true, senderUser);

        User originalAuthorUser = user("original", "Original");
        ReflectionTestUtils.setField(originalAuthorUser, "id", 99L);
        WorkspaceMember originalAuthor = workspaceMember(50L, workspace, true, originalAuthorUser);
        Thread replyTo = thread(50L, channel, originalAuthor, "원본 메시지", LocalDateTime.of(2026, 6, 23, 9, 0));

        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("reply content", 50L, null);

        when(entityManager.find(Channel.class, channelId)).thenReturn(channel);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(sender));
        when(threadRepository.findById(50L)).thenReturn(Optional.of(replyTo));
        when(threadRepository.save(org.mockito.ArgumentMatchers.any(Thread.class))).thenAnswer(invocation -> {
            Thread saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 100L);
            ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 0));
            return saved;
        });

        chatMessageService.createChannelMessage(channelId, userId, request);

        verify(workspaceEventService).recordEvent(
                workspaceId, WorkspaceEvent.EventType.REPLY, "Sender", null, null, channelId,
                "reply content", null, null, 50L, null, null, 99L);
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
