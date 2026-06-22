package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ThreadAttachmentRepository extends JpaRepository<ThreadAttachment, Long> {

    List<ThreadAttachment> findAllByThread_IdOrderByIdAsc(Long threadId);

    List<ThreadAttachment> findAllByThread_IdInOrderByThread_IdAscIdAsc(List<Long> threadIds);

    @Query("""
            SELECT ta FROM ThreadAttachment ta
            WHERE ta.thread.channel.id = :channelId
              AND ta.attachmentType = 'pr'
            """)
    List<ThreadAttachment> findAllPrByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = """
            DELETE FROM thread_attachments
            WHERE thread_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void deleteAllByThreadChannelId(@Param("channelId") Long channelId);
}
