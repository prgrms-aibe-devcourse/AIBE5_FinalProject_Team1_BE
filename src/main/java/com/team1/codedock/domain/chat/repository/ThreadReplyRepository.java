package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ThreadReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThreadReplyRepository extends JpaRepository<ThreadReply, Long> {

    List<ThreadReply> findAllByThread_IdOrderByCreatedAtAscIdAsc(Long threadId);

    @Modifying
    @Query(value = """
            UPDATE pull_request_reviews
            SET thread_reply_id = NULL
            WHERE thread_reply_id IN (
                SELECT tr.id
                FROM thread_replies tr
                JOIN threads t ON t.id = tr.thread_id
                WHERE t.channel_id = :channelId
            )
            """, nativeQuery = true)
    void clearPullRequestReviewReferencesByThreadChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            UPDATE pull_request_review_comments
            SET thread_reply_id = NULL
            WHERE thread_reply_id IN (
                SELECT tr.id
                FROM thread_replies tr
                JOIN threads t ON t.id = tr.thread_id
                WHERE t.channel_id = :channelId
            )
            """, nativeQuery = true)
    void clearPullRequestReviewCommentReferencesByThreadChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            DELETE FROM thread_replies
            WHERE thread_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void deleteAllByThreadChannelId(@Param("channelId") Long channelId);
}
