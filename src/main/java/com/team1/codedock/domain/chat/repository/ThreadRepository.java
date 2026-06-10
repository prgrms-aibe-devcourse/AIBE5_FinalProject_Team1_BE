package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.Thread;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ThreadRepository extends JpaRepository<Thread, Long> {

    boolean existsByChannel_Id(Long channelId);

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
}
