package com.team1.codedock.domain.chat.repository;

import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChannelReadStatusRepository extends JpaRepository<ChannelReadStatus, Long> {

    boolean existsByChannel_Id(Long channelId);

    // 한 멤버는 한 채널에 하나의 읽음 상태만 가짐
    Optional<ChannelReadStatus> findByChannel_IdAndWorkspaceMember_Id(Long channelId, Long workspaceMemberId);

    // 채널 삭제 시 해당 채널의 읽음 상태를 함께 정리함
    void deleteAllByChannel_Id(Long channelId);
}
