package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Mention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    // 내 멘션 목록을 최신 생성 순서로 조회함
    List<Mention> findAllByWorkspace_IdAndMentionedMember_IdOrderByCreatedAtDesc(
            Long workspaceId,
            Long mentionedMemberId
    );

    Optional<Mention> findByIdAndMentionedMember_Id(Long id, Long mentionedMemberId);

    @Modifying
    @Query(value = """
            DELETE FROM mentions
            WHERE thread_reply_id IN (
                SELECT tr.id
                FROM thread_replies tr
                JOIN threads t ON t.id = tr.thread_id
                WHERE t.channel_id = :channelId
            )
            """, nativeQuery = true)
    void deleteAllByThreadReplyChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            DELETE FROM mentions
            WHERE thread_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void deleteAllByThreadChannelId(@Param("channelId") Long channelId);
}
