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

    // 채널을 읽음 처리할 때 기준점으로 삼을 최신 사용자 메시지를 찾는다.
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
