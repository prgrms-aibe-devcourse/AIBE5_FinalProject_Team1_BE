package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.entity.ChannelReadStatus;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChannelReadStatusService {

    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ThreadRepository threadRepository;
    private final ChannelReadStatusRepository channelReadStatusRepository;

    // 채널의 최신 사용자 메시지를 멤버의 읽음 기준점으로 저장함
    @Transactional
    public ChannelReadStatusResponse markChannelAsRead(Long channelId, Long userId) {
        Channel channel = findChannel(channelId);
        WorkspaceMember member = findActiveWorkspaceMember(channel, userId);
        Thread latestThread = findLatestChannelMessage(channelId);
        LocalDateTime readAt = LocalDateTime.now();

        ChannelReadStatus status = channelReadStatusRepository
                .findByChannel_IdAndWorkspaceMember_Id(channelId, member.getId())
                .map(existingStatus -> {
                    existingStatus.updateLastRead(latestThread, readAt);
                    return existingStatus;
                })
                .orElseGet(() -> channelReadStatusRepository.save(
                        ChannelReadStatus.create(channel, member, latestThread, readAt)
                ));

        return ChannelReadStatusResponse.from(status);
    }

    // 존재하지 않는 채널이면 읽음 상태를 만들 수 없으므로 예외 처리함
    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
    }

    // JWT 연동 전까지 X-User-Id로 워크스페이스 멤버 확인함
    private WorkspaceMember findActiveWorkspaceMember(Channel channel, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Long workspaceId = channel.getWorkspace().getId();
        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    // 메시지가 없는 채널도 읽음 처리할 수 있으므로 최신 메시지가 없으면 null 저장함
    private Thread findLatestChannelMessage(Long channelId) {
        return threadRepository
                .findFirstByChannel_IdAndThreadTypeOrderByIdDesc(channelId, Thread.TYPE_USER_MESSAGE)
                .orElse(null);
    }
}
