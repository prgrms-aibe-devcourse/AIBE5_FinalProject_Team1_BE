package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private static final Set<String> ALLOWED_REACTION_KEYS = Set.of(
            "like",
            "dislike",
            "heart",
            "laugh",
            "smile",
            "surprised",
            "sad",
            "cry",
            "angry",
            "thinking",
            "clap",
            "pray",
            "eyes",
            "fire",
            "rocket",
            "party",
            "check",
            "cross",
            "star",
            "bulb",
            "bug",
            "fix",
            "memo",
            "coffee"
    );

    private static final Map<String, String> REACTION_ALIASES = Map.ofEntries(
            Map.entry("thumbs_up", "like"),
            Map.entry("thumbsup", "like"),
            Map.entry("+1", "like"),
            Map.entry("\uD83D\uDC4D", "like"),
            Map.entry("thumbs_down", "dislike"),
            Map.entry("thumbsdown", "dislike"),
            Map.entry("-1", "dislike"),
            Map.entry("\uD83D\uDC4E", "dislike"),
            Map.entry("\u2764", "heart"),
            Map.entry("\u2764\uFE0F", "heart"),
            Map.entry("\uD83D\uDE02", "laugh"),
            Map.entry("\uD83D\uDE04", "smile"),
            Map.entry("\uD83D\uDE2E", "surprised"),
            Map.entry("\uD83D\uDE22", "sad"),
            Map.entry("\uD83D\uDE2D", "cry"),
            Map.entry("\uD83D\uDE21", "angry"),
            Map.entry("\uD83E\uDD14", "thinking"),
            Map.entry("\uD83D\uDC4F", "clap"),
            Map.entry("\uD83D\uDE4F", "pray"),
            Map.entry("\uD83D\uDC40", "eyes"),
            Map.entry("\uD83D\uDD25", "fire"),
            Map.entry("\uD83D\uDE80", "rocket"),
            Map.entry("\uD83C\uDF89", "party"),
            Map.entry("\u2705", "check"),
            Map.entry("\u274C", "cross"),
            Map.entry("\u2B50", "star"),
            Map.entry("\uD83D\uDCA1", "bulb"),
            Map.entry("\uD83D\uDC1B", "bug"),
            Map.entry("\uD83D\uDD27", "fix"),
            Map.entry("\uD83D\uDCDD", "memo"),
            Map.entry("\u2615", "coffee"),
            Map.entry("\u2615\uFE0F", "coffee")
    );

    private final ReactionRepository reactionRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;

    @Transactional
    public ReactionToggleResponse toggleReaction(Long channelId, Long userId, ReactionToggleRequest request) {
        validateTargetType(request.targetType());
        String reactionKey = normalizeReactionKey(request.emoji());
        Channel targetChannel = resolveTargetChannel(request.targetType(), request.targetId());
        validateTargetBelongsToChannel(channelId, targetChannel);

        WorkspaceMember workspaceMember = findActiveWorkspaceMember(targetChannel, userId);

        boolean reacted = toggle(request.targetType(), request.targetId(), reactionKey, workspaceMember);
        long count = reactionRepository.countByTargetTypeAndTargetIdAndEmoji(
                request.targetType(),
                request.targetId(),
                reactionKey
        );

        return ReactionToggleResponse.of(
                channelId,
                workspaceMember.getId(),
                request.targetType(),
                request.targetId(),
                reactionKey,
                reacted,
                count
        );
    }

    @Transactional(readOnly = true)
    public List<ReactionSummaryResponse> getReactionSummaries(Long channelId) {
        // 초기 렌더링에서 메시지와 답글 리액션을 한 번에 붙일 수 있도록 집계함
        List<ReactionSummaryResponse> summaries = new ArrayList<>();
        summaries.addAll(reactionRepository.findThreadReactionSummariesByChannelId(channelId));
        summaries.addAll(reactionRepository.findThreadReplyReactionSummariesByChannelId(channelId));
        return summaries;
    }

    private boolean toggle(
            String targetType,
            Long targetId,
            String reactionKey,
            WorkspaceMember workspaceMember
    ) {
        return reactionRepository.findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
                        workspaceMember.getId(),
                        targetType,
                        targetId,
                        reactionKey
                )
                .map(reaction -> {
                    reactionRepository.delete(reaction);
                    return false;
                })
                .orElseGet(() -> {
                    Reaction reaction = Reaction.create(
                            workspaceMember,
                            targetType,
                            targetId,
                            reactionKey
                    );
                    reactionRepository.save(reaction);
                    return true;
                });
    }

    private String normalizeReactionKey(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리액션 값은 필수입니다.");
        }

        String trimmed = emoji.trim();
        String lowerCase = trimmed.toLowerCase(Locale.ROOT);

        if (ALLOWED_REACTION_KEYS.contains(lowerCase)) {
            return lowerCase;
        }

        String aliasKey = REACTION_ALIASES.getOrDefault(trimmed, REACTION_ALIASES.get(lowerCase));
        if (aliasKey != null) {
            return aliasKey;
        }

        // Oracle 문자셋 이슈 방지를 위해 DB에는 실제 이모지가 아니라 허용된 reaction key만 저장함
        throw new BusinessException(ErrorCode.INVALID_INPUT, "허용되지 않은 리액션입니다.");
    }

    private void validateTargetType(String targetType) {
        if (!Reaction.TARGET_TYPE_THREAD.equals(targetType)
                && !Reaction.TARGET_TYPE_THREAD_REPLY.equals(targetType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리액션 대상 타입은 thread 또는 thread_reply만 가능합니다.");
        }
    }

    private void validateTargetBelongsToChannel(Long channelId, Channel targetChannel) {
        if (!channelId.equals(targetChannel.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "리액션 대상이 요청한 채널에 속하지 않습니다.");
        }
    }

    private Channel resolveTargetChannel(String targetType, Long targetId) {
        if (Reaction.TARGET_TYPE_THREAD.equals(targetType)) {
            Thread thread = threadRepository.findById(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "메시지를 찾을 수 없습니다."));
            return thread.getChannel();
        }

        ThreadReply threadReply = entityManager.find(ThreadReply.class, targetId);
        if (threadReply == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "스레드 답글을 찾을 수 없습니다.");
        }
        return threadReply.getThread().getChannel();
    }

    private WorkspaceMember findActiveWorkspaceMember(Channel channel, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long workspaceId = channel.getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
