package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelReadStatusRepository extends JpaRepository<ChannelReadStatus, Long> {

    boolean existsByChannel_Id(Long channelId);
}
