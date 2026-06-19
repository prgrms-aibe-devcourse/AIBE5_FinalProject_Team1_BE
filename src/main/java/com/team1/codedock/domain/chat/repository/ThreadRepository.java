package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.entity.Thread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThreadRepository extends JpaRepository<Thread, Long> {

    @Query("SELECT COUNT(t) FROM Thread t WHERE t.channel.id = :channelId")
    long countByChannelId(@Param("channelId") Long channelId);

    @Query("SELECT t.channel.workspace.id FROM Thread t WHERE t.id = :threadId")
    Optional<Long> findWorkspaceIdById(@Param("threadId") Long threadId);

    // 채널 읽음 처리 기준으로 읽을 최신 사용자 메시지 조회함
    Optional<Thread> findFirstByChannel_IdAndThreadTypeOrderByIdDesc(Long channelId, String threadType);

    List<Thread> findAllByChannel_IdAndThreadTypeOrderByIdDesc(
            Long channelId,
            String threadType,
            Pageable pageable
    );

    List<Thread> findAllByChannel_IdAndThreadTypeAndIdLessThanOrderByIdDesc(
            Long channelId,
            String threadType,
            Long cursor,
            Pageable pageable
    );

    @Query("""
            select t.channel.id as channelId, count(t) as messageCount
            from Thread t
            where t.channel.id in :channelIds
              and t.threadType = :threadType
            group by t.channel.id
            """)
    List<ChannelMessageCountProjection> countByChannelIdsAndThreadType(
            @Param("channelIds") List<Long> channelIds,
            @Param("threadType") String threadType
    );

    // 워크스페이스 멤버 기준으로 채널별 안 읽은 사용자 메시지 수 집계함
    // 읽음 상태가 없으면 해당 채널의 모든 사용자 메시지를 unread로 봄
    @Query("""
            select t.channel.id as channelId, count(t) as messageCount
            from Thread t
            left join ChannelReadStatus crs
              on crs.channel = t.channel
             and crs.workspaceMember.id = :workspaceMemberId
            where t.channel.id in :channelIds
              and t.threadType = :threadType
              and (
                  crs.id is null
                  or crs.lastReadThread is null
                  or t.id > crs.lastReadThread.id
              )
            group by t.channel.id
            """)
    List<ChannelMessageCountProjection> countUnreadByChannelIdsAndThreadType(
            @Param("channelIds") List<Long> channelIds,
            @Param("threadType") String threadType,
            @Param("workspaceMemberId") Long workspaceMemberId
    );

    @Query("""
            select t
            from Thread t
            where t.channel.id in :channelIds
              and t.threadType = :threadType
              and not exists (
                  select 1
                  from Thread newer
                  where newer.channel.id = t.channel.id
                    and newer.threadType = :threadType
                    and (
                        newer.createdAt > t.createdAt
                        or (newer.createdAt = t.createdAt and newer.id > t.id)
                    )
              )
            """)
    List<Thread> findLatestByChannelIdsAndThreadType(
            @Param("channelIds") List<Long> channelIds,
            @Param("threadType") String threadType
    );

    @Query("SELECT t FROM Thread t WHERE t.channel.id = :channelId AND t.threadType IN :threadTypes ORDER BY t.id DESC")
    List<Thread> findAllByChannel_IdAndThreadTypeInOrderByIdDesc(
            @Param("channelId") Long channelId,
            @Param("threadTypes") List<String> threadTypes,
            Pageable pageable
    );

    @Query("SELECT t FROM Thread t WHERE t.channel.id = :channelId AND t.threadType IN :threadTypes AND t.id < :cursor ORDER BY t.id DESC")
    List<Thread> findAllByChannel_IdAndThreadTypeInAndIdLessThanOrderByIdDesc(
            @Param("channelId") Long channelId,
            @Param("threadTypes") List<String> threadTypes,
            @Param("cursor") Long cursor,
            Pageable pageable
    );

    @Query("SELECT t FROM Thread t WHERE t.threadableType = :threadableType AND t.threadableId = :threadableId")
    Optional<Thread> findByThreadableTypeAndThreadableId(
            @Param("threadableType") String threadableType,
            @Param("threadableId") Long threadableId
    );

    @Query("SELECT c FROM Channel c WHERE c.githubRepository.id = :githubRepositoryId")
    Optional<Channel> findChannelByGithubRepositoryId(@Param("githubRepositoryId") Long githubRepositoryId);

    @Modifying
    @Query(value = """
            UPDATE threads
            SET reply_to_id = NULL
            WHERE reply_to_id IN (
                SELECT id
                FROM threads
                WHERE channel_id = :channelId
            )
            """, nativeQuery = true)
    void clearReplyToByChannelId(@Param("channelId") Long channelId);

    @Modifying
    @Query(value = "DELETE FROM threads WHERE channel_id = :channelId", nativeQuery = true)
    void deleteAllByChannelId(@Param("channelId") Long channelId);
}
