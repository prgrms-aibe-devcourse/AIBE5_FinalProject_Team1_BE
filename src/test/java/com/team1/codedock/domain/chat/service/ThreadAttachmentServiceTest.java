package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.user.entity.User;
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

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadAttachmentServiceTest {

    @Mock
    private ThreadAttachmentRepository threadAttachmentRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @InjectMocks
    private ThreadAttachmentService threadAttachmentService;

    @Test
    @DisplayName("Active workspace member can add attachments to channel message")
    void addAttachments() {
        Long channelId = 1L;
        Long messageId = 100L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true);
        Thread message = message(messageId, channel, member);
        ThreadAttachmentRequest request = request("IMAGE");

        when(threadRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadAttachmentRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> {
                    List<ThreadAttachment> attachments = invocation.getArgument(0);
                    ReflectionTestUtils.setField(attachments.get(0), "id", 1L);
                    return attachments;
                });

        List<ThreadAttachmentResponse> responses =
                threadAttachmentService.addAttachments(channelId, messageId, userId, List.of(request));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(1L);
        assertThat(responses.get(0).attachmentType()).isEqualTo("image");
        assertThat(responses.get(0).url()).isEqualTo("https://example.com/file.png");
        verify(threadAttachmentRepository).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Unsupported attachment type is rejected")
    void addAttachmentsWithUnsupportedType() {
        Long channelId = 1L;
        Long messageId = 100L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true);
        Thread message = message(messageId, channel, member);

        when(threadRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                channelId,
                messageId,
                userId,
                List.of(request("video"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("File image and link attachments require url")
    void addAttachmentsWithMissingUrl() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true);
        Thread message = message(100L, channel, member);
        ThreadAttachmentRequest request = new ThreadAttachmentRequest(
                "image",
                null,
                null,
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L
        );

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                channelId,
                100L,
                userId,
                List.of(request)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Domain attachments require targetId or url")
    void addDomainAttachmentWithoutTargetOrUrl() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true);
        Thread message = message(100L, channel, member);
        ThreadAttachmentRequest request = new ThreadAttachmentRequest(
                "pr",
                null,
                null,
                "PR #142",
                null,
                null,
                null,
                null,
                null
        );

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                channelId,
                100L,
                userId,
                List.of(request)
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Cannot add attachments to deleted message")
    void addAttachmentsToDeletedMessage() {
        Long channelId = 1L;
        Workspace workspace = workspace(2L);
        Thread message = message(100L, channel(channelId, workspace), workspaceMember(10L, workspace, true));
        ReflectionTestUtils.setField(message, "content", Thread.DELETED_MESSAGE_CONTENT);

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                channelId,
                100L,
                3L,
                List.of(request("file"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Domain attachment can use targetId")
    void addDomainAttachmentWithTargetId() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(channelId, workspace);
        WorkspaceMember member = workspaceMember(10L, workspace, true);
        Thread message = message(100L, channel, member);
        ThreadAttachmentRequest request = new ThreadAttachmentRequest(
                "pr",
                142L,
                null,
                "PR #142",
                null,
                null,
                null,
                null,
                null
        );

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadAttachmentRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<ThreadAttachmentResponse> responses =
                threadAttachmentService.addAttachments(channelId, 100L, userId, List.of(request));

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).targetId()).isEqualTo(142L);
        assertThat(responses.get(0).type()).isEqualTo("pr");
    }

    @Test
    @DisplayName("Cannot add attachments to message from another channel")
    void addAttachmentsWithDifferentChannel() {
        Long requestedChannelId = 1L;
        Thread message = message(100L, channel(99L, workspace(2L)), workspaceMember(10L, workspace(2L), true));

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                requestedChannelId,
                100L,
                3L,
                List.of(request("file"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    @DisplayName("Inactive or non-member user cannot add attachments")
    void addAttachmentsWithForbiddenUser() {
        Long channelId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Thread message = message(100L, channel(channelId, workspace), workspaceMember(10L, workspace, true));

        when(threadRepository.findById(100L)).thenReturn(Optional.of(message));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> threadAttachmentService.addAttachments(
                channelId,
                100L,
                userId,
                List.of(request("file"))
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadAttachmentRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }

    private static ThreadAttachmentRequest request(String attachmentType) {
        return new ThreadAttachmentRequest(
                attachmentType,
                null,
                "https://example.com/file.png",
                "file.png",
                null,
                null,
                null,
                "image/png",
                100L
        );
    }

    private static Thread message(Long id, Channel channel, WorkspaceMember sender) {
        Thread thread = newInstance(Thread.class);
        ReflectionTestUtils.setField(thread, "id", id);
        ReflectionTestUtils.setField(thread, "channel", channel);
        ReflectionTestUtils.setField(thread, "createdBy", sender);
        ReflectionTestUtils.setField(thread, "threadType", Thread.TYPE_USER_MESSAGE);
        ReflectionTestUtils.setField(thread, "content", "hello");
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

    private static WorkspaceMember workspaceMember(Long id, Workspace workspace, boolean isActive) {
        WorkspaceMember member = newInstance(WorkspaceMember.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "workspace", workspace);
        ReflectionTestUtils.setField(member, "isActive", isActive);
        ReflectionTestUtils.setField(member, "user", newInstance(User.class));
        return member;
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
