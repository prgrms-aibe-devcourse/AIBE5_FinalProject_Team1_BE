package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Thread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ThreadRepository extends JpaRepository<Thread, Long> {

    boolean existsByChannel_Id(Long channelId);

    // 채널을 읽음 처리할 때 기준점으로 삼을 최신 사용자 메시지를 찾음
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

    // 채널별 안 읽은 메시지 수를 한 번에 집계
    // 읽음 상태가 없거나 마지막 읽은 메시지가 없으면 해당 채널의 모든 사용자 메시지를 unread로 확인
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
}
