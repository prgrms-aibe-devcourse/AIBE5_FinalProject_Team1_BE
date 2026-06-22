package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.dto.ReactionSummaryResponse;
import com.team1.codedock.domain.chat.entity.Reaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReactionRepository extends JpaRepository<Reaction, Long> {

    Optional<Reaction> findByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
            Long workspaceMemberId,
            String targetType,
            Long targetId,
            String emoji
    );

    long countByTargetTypeAndTargetIdAndEmoji(String targetType, Long targetId, String emoji);

    boolean existsByWorkspaceMember_IdAndTargetTypeAndTargetIdAndEmoji(
            Long workspaceMemberId,
            String targetType,
            Long targetId,
            String emoji
    );

    // 채널 메시지(thread)에 달린 리액션을 이모지별로 묶어 초기 화면용 개수 생성
    @Query("""
            select new com.team1.codedock.domain.chat.dto.ReactionSummaryResponse(
                r.targetType,
                r.targetId,
                r.emoji,
                count(r)
            )
            from Reaction r, Thread t
            where r.targetType = 'thread'
              and r.targetId = t.id
              and t.channel.id = :channelId
            group by r.targetType, r.targetId, r.emoji
            """)
    List<ReactionSummaryResponse> findThreadReactionSummariesByChannelId(@Param("channelId") Long channelId);

    // 답글(thread_reply)은 직접 channel_id가 없어서 부모 thread를 스레드를 따라가며 채널을 확인함
    @Query("""
            select new com.team1.codedock.domain.chat.dto.ReactionSummaryResponse(
                r.targetType,
                r.targetId,
                r.emoji,
                count(r)
            )
            from Reaction r, ThreadReply tr
            where r.targetType = 'thread_reply'
              and r.targetId = tr.id
              and tr.thread.channel.id = :channelId
            group by r.targetType, r.targetId, r.emoji
            """)
    List<ReactionSummaryResponse> findThreadReplyReactionSummariesByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            DELETE FROM reactions
            WHERE target_type = 'thread_reply'
              AND target_id IN (
                  SELECT tr.id
                  FROM thread_replies tr
                  JOIN threads t ON t.id = tr.thread_id
                  WHERE t.channel_id = :channelId
              )
            """, nativeQuery = true)
    void deleteAllThreadReplyReactionsByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            DELETE FROM reactions
            WHERE target_type = 'thread'
              AND target_id IN (
                  SELECT id
                  FROM threads
                  WHERE channel_id = :channelId
              )
            """, nativeQuery = true)
    void deleteAllThreadReactionsByChannelId(@Param("channelId") Long channelId);
}
