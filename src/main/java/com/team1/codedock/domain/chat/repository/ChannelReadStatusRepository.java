package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChannelReadStatusRepository extends JpaRepository<ChannelReadStatus, Long> {

    boolean existsByChannel_Id(Long channelId);

    // 한 멤버는 한 채널에 하나의 읽음 상태만 가짐
    Optional<ChannelReadStatus> findByChannel_IdAndWorkspaceMember_Id(Long channelId, Long workspaceMemberId);

    @Modifying
    @Query(value = """
            UPDATE channel_read_status
            SET last_read_thread_id = NULL
            WHERE last_read_thread_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void clearLastReadThreadByThreadChannelId(@Param("channelId") Long channelId);

    // 채널 삭제 시 해당 채널의 읽음 상태를 함께 정리함
    @Modifying
    @Query(value = "DELETE FROM channel_read_status WHERE channel_id = :channelId", nativeQuery = true)
    void deleteAllByChannelId(@Param("channelId") Long channelId);
}
