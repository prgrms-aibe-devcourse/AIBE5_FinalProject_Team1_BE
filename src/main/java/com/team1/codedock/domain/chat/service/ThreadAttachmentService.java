package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ThreadAttachmentRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ThreadAttachmentService {

    private static final int MAX_ATTACHMENTS_PER_REQUEST = 10;
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES =
            Set.of("file", "image", "link", "pr", "issue", "api", "erd", "docs");

    private final ThreadAttachmentRepository threadAttachmentRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @Transactional
    public List<ThreadAttachmentResponse> addAttachments(
            Long channelId,
            Long messageId,
            Long userId,
            List<ThreadAttachmentRequest> requests
    ) {
        Thread thread = findUserMessage(messageId);
        validateMessageBelongsToChannel(thread, channelId);
        validateActiveWorkspaceMember(thread, userId);

        return saveAttachments(thread, requests);
    }

    @Transactional
    public void deleteAttachment(
            Long channelId,
            Long messageId,
            Long attachmentId,
            Long userId
    ) {
        Thread thread = findUserMessage(messageId);
        validateMessageBelongsToChannel(thread, channelId);
        WorkspaceMember member = validateActiveWorkspaceMember(thread, userId);
        validateMessageAuthor(thread, member);

        ThreadAttachment attachment = threadAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Attachment not found."));
        validateAttachmentBelongsToMessage(attachment, thread);

        threadAttachmentRepository.delete(attachment);
    }

    @Transactional
    public List<ThreadAttachmentResponse> saveAttachments(Thread thread, List<ThreadAttachmentRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        validateAttachmentRequestList(requests);

        List<ThreadAttachment> attachments = requests.stream()
                .map(request -> createAttachment(thread, request))
                .toList();

        return threadAttachmentRepository.saveAll(attachments).stream()
                .map(ThreadAttachmentResponse::from)
                .toList();
    }

    private void validateAttachmentRequestList(List<ThreadAttachmentRequest> requests) {
        if (requests.size() > MAX_ATTACHMENTS_PER_REQUEST) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Attachments must be 10 or fewer.");
        }
        if (requests.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Attachment must not be null.");
        }
    }

    @Transactional(readOnly = true)
    public List<ThreadAttachmentResponse> getAttachments(Long threadId) {
        if (threadId == null) {
            return List.of();
        }

        return threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(threadId).stream()
                .map(ThreadAttachmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<ThreadAttachmentResponse>> getAttachmentMap(List<Long> threadIds) {
        if (threadIds == null || threadIds.isEmpty()) {
            return Map.of();
        }

        return threadAttachmentRepository.findAllByThread_IdInOrderByThread_IdAscIdAsc(threadIds).stream()
                .collect(Collectors.groupingBy(
                        attachment -> attachment.getThread().getId(),
                        Collectors.mapping(ThreadAttachmentResponse::from, Collectors.toList())
                ));
    }

    private ThreadAttachment createAttachment(Thread thread, ThreadAttachmentRequest request) {
        String attachmentType = normalizeAttachmentType(request.attachmentType());
        validateAttachmentType(attachmentType);
        validateAttachmentPayload(attachmentType, request);

        return ThreadAttachment.create(
                thread,
                attachmentType,
                request.targetId(),
                request.url(),
                request.title(),
                request.detail(),
                request.meta(),
                request.previewUrl(),
                request.mimeType(),
                request.fileSize()
        );
    }

    private String normalizeAttachmentType(String attachmentType) {
        if (attachmentType == null) {
            return null;
        }
        return attachmentType.trim().toLowerCase();
    }

    private void validateAttachmentType(String attachmentType) {
        if (!ALLOWED_ATTACHMENT_TYPES.contains(attachmentType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Unsupported attachment type.");
        }
    }

    private void validateAttachmentPayload(String attachmentType, ThreadAttachmentRequest request) {
        if (requiresUrl(attachmentType) && isBlank(request.url())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Attachment url is required.");
        }
    }

    private boolean requiresUrl(String attachmentType) {
        return "file".equals(attachmentType) || "image".equals(attachmentType) || "link".equals(attachmentType);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private Thread findUserMessage(Long messageId) {
        Thread thread = threadRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.THREAD_NOT_FOUND));
        if (!Thread.TYPE_USER_MESSAGE.equals(thread.getThreadType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Only user messages can have attachments.");
        }
        if (Thread.DELETED_MESSAGE_CONTENT.equals(thread.getContent())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Deleted message cannot have attachments.");
        }
        return thread;
    }

    private void validateMessageBelongsToChannel(Thread thread, Long channelId) {
        if (!thread.getChannel().getId().equals(channelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Message does not belong to the requested channel.");
        }
    }

    private WorkspaceMember validateActiveWorkspaceMember(Thread thread, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long workspaceId = thread.getChannel().getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void validateMessageAuthor(Thread thread, WorkspaceMember member) {
        if (thread.getCreatedBy() == null || !member.getId().equals(thread.getCreatedBy().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateAttachmentBelongsToMessage(ThreadAttachment attachment, Thread thread) {
        if (!thread.getId().equals(attachment.getThread().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Attachment does not belong to the requested message.");
        }
    }
}
