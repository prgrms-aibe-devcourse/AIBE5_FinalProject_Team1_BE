package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ReactionSummaryResponse;
import com.team1.codedock.domain.chat.dto.ReactionToggleRequest;
import com.team1.codedock.domain.chat.dto.ReactionToggleResponse;
import com.team1.codedock.domain.chat.entity.Reaction;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ReactionRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository reactionRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;

    @Transactional
    public ReactionToggleResponse toggleReaction(Long channelId, ReactionToggleRequest request) {
        validateTargetType(request.targetType());
        validateTargetBelongsToChannel(channelId, request.targetType(), request.targetId());

        WorkspaceMember workspaceMember = workspaceMemberRepository.findById(request.workspaceMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        boolean reacted = toggle(request, workspaceMember);
        long count = reactionRepository.countByTargetTypeAndTargetIdAndEmoji(
                request.targetType(),
                request.targetId(),
                request.emoji()
        );

        return ReactionToggleResponse.of(
                channelId,
                request.workspaceMemberId(),
                request.targetType(),
                request.targetId(),
                request.emoji(),
                reacted,
                count
        );
    }

    @Transactional(readOnly = true)
    public List<ReactionSummaryResponse> getReactionSummaries(Long channelId) {
        // 프론트 초기 렌더링에서 메시지와 답글 리액션을 한 번에 붙일 수 있도록 두 집계를 합친다.
        List<ReactionSummaryResponse> summaries = new ArrayList<>();
        summaries.addAll(reactionRepository.findThreadReactionSummariesByChannelId(channelId));
        summaries.addAll(reactionRepository.findThreadReplyReactionSummariesByChannelId(channelId));
        return summaries;
    }

    private boolean toggle(ReactionToggleRequest request, WorkspaceMember workspaceMember) {
        return reactionRepository.findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
                        request.workspaceMemberId(),
                        request.targetType(),
                        request.targetId(),
                        request.emoji()
                )
                .map(reaction -> {
                    reactionRepository.delete(reaction);
                    return false;
                })
                .orElseGet(() -> {
                    Reaction reaction = Reaction.create(
                            workspaceMember,
                            request.targetType(),
                            request.targetId(),
                            request.emoji()
                    );
                    reactionRepository.save(reaction);
                    return true;
                });
    }

    private void validateTargetType(String targetType) {
        if (!Reaction.TARGET_TYPE_THREAD.equals(targetType)
                && !Reaction.TARGET_TYPE_THREAD_REPLY.equals(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리액션 대상 타입은 thread 또는 thread_reply만 가능합니다.");
        }
    }

    private void validateTargetBelongsToChannel(Long channelId, String targetType, Long targetId) {
        Long targetChannelId = resolveTargetChannelId(targetType, targetId);
        if (!channelId.equals(targetChannelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리액션 대상이 요청한 채널에 속하지 않습니다.");
        }
    }

    private Long resolveTargetChannelId(String targetType, Long targetId) {
        if (Reaction.TARGET_TYPE_THREAD.equals(targetType)) {
            Thread thread = threadRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없습니다."));
            return thread.getChannel().getId();
        }

        ThreadReply threadReply = entityManager.find(ThreadReply.class, targetId);
        if (threadReply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "스레드 답글을 찾을 수 없습니다.");
        }
        return threadReply.getThread().getChannel().getId();
    }
}
