package com.team1.codedock.domain.channel.repository;

import com.team1.codedock.domain.channel.entity.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    List<Channel> findAllByWorkspace_IdOrderByIdAsc(Long workspaceId);
}
