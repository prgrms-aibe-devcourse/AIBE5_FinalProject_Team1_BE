package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyUpdateRequest;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ThreadReplyService {

    private final ThreadReplyRepository threadReplyRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;
    private final MentionService mentionService;

    @Transactional(readOnly = true)
    public List<ThreadReplyResponse> getReplies(Long threadId, Long userId) {
        Thread thread = findThread(threadId);
        validateWorkspaceMember(thread, userId);

        return threadReplyRepository.findAllByThread_IdOrderByCreatedAtAscIdAsc(threadId).stream()
                .map(ThreadReplyResponse::from)
                .toList();
    }

    @Transactional
    public ThreadReplyResponse createReply(Long threadId, Long userId, ThreadReplyCreateRequest request) {
        validateContent(request.content());
        Thread thread = findThread(threadId);
        // 요청 body가 아니라 인증 userId로 답글 작성 멤버 확인함
        WorkspaceMember member = findActiveWorkspaceMember(thread, userId);

        ThreadReply reply = ThreadReply.create(thread, member, ChatContentEmojiCodec.encode(request.content()));
        ThreadReply savedReply = threadReplyRepository.save(reply);
        mentionService.createMentionsForThreadReply(savedReply, member, request.content());
        return ThreadReplyResponse.from(savedReply);
    }

    @Transactional
    public ThreadReplyResponse updateReply(
            Long threadId,
            Long replyId,
            Long userId,
            ThreadReplyUpdateRequest request
    ) {
        validateContent(request.content());
        Thread thread = findThread(threadId);
        WorkspaceMember member = findActiveWorkspaceMember(thread, userId);
        ThreadReply reply = findEditableReply(thread, replyId, member);
        validateReplyNotDeleted(reply);

        reply.updateContent(ChatContentEmojiCodec.encode(request.content()));
        return ThreadReplyResponse.from(reply);
    }

    @Transactional
    public ThreadReplyResponse deleteReply(Long threadId, Long replyId, Long userId) {
        Thread thread = findThread(threadId);
        WorkspaceMember member = findActiveWorkspaceMember(thread, userId);
        ThreadReply reply = findEditableReply(thread, replyId, member);

        reply.markAsDeleted();
        return ThreadReplyResponse.from(reply);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "답글 내용은 필수입니다.");
        }
    }

    private Thread findThread(Long threadId) {
        Thread thread = entityManager.find(Thread.class, threadId);
        if (thread == null) {
            throw new BusinessException(ErrorCode.THREAD_NOT_FOUND);
        }
        return thread;
    }

    private void validateWorkspaceMember(Thread thread, Long userId) {
        findActiveWorkspaceMember(thread, userId);
    }

    private WorkspaceMember findActiveWorkspaceMember(Thread thread, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // 답글 대상 스레드가 속한 워크스페이스에서 활성 멤버인지 확인함
        Long workspaceId = thread.getChannel().getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private ThreadReply findEditableReply(Thread thread, Long replyId, WorkspaceMember member) {
        ThreadReply reply = threadReplyRepository.findById(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "답글을 찾을 수 없습니다."));

        validateReplyBelongsToThread(thread, reply);
        validateReplyAuthor(reply, member);
        return reply;
    }

    private void validateReplyBelongsToThread(Thread thread, ThreadReply reply) {
        if (!thread.getId().equals(reply.getThread().getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "요청한 스레드에 속한 답글이 아닙니다.");
        }
    }

    private void validateReplyAuthor(ThreadReply reply, WorkspaceMember member) {
        if (!member.getId().equals(reply.getWorkspaceMember().getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateReplyNotDeleted(ThreadReply reply) {
        if (reply.isDeleted()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "삭제된 답글은 수정할 수 없습니다.");
        }
    }
}
