package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
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
        WorkspaceMember member = findActiveWorkspaceMember(thread, userId);

        ThreadReply reply = ThreadReply.create(thread, member, request.content());
        ThreadReply savedReply = threadReplyRepository.save(reply);
        mentionService.createMentionsForThreadReply(savedReply, member, request.content());
        return ThreadReplyResponse.from(savedReply);
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Reply content must not be blank.");
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

        Long workspaceId = thread.getChannel().getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
