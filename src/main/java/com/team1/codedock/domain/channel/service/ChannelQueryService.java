package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.repository.ChannelMessageCountProjection;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelQueryService {

    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ThreadRepository threadRepository;

    public List<ChannelListResponse> getChannels(Long workspaceId, Long userId) {
        WorkspaceMember member = findActiveWorkspaceMember(workspaceId, userId);

        List<Channel> channels = channelRepository.findAllByWorkspace_IdOrderByIdAsc(workspaceId);
        if (channels.isEmpty()) {
            return List.of();
        }

        List<Long> channelIds = channels.stream()
                .map(Channel::getId)
                .toList();
        Map<Long, Long> messageCounts = getMessageCounts(channelIds);
        // unread count는 전체 사용자가 아니라 현재 워크스페이스 멤버 기준으로 계산함
        Map<Long, Long> unreadCounts = getUnreadCounts(channelIds, member.getId());
        Map<Long, Thread> latestMessages = getLatestMessages(channelIds);

        return channels.stream()
                .map(channel -> {
                    Thread latestMessage = latestMessages.get(channel.getId());
                    return ChannelListResponse.from(
                            channel,
                            latestMessage == null ? null : latestMessage.getContent(),
                            latestMessage == null ? null : latestMessage.getCreatedAt(),
                            messageCounts.getOrDefault(channel.getId(), 0L),
                            unreadCounts.getOrDefault(channel.getId(), 0L)
                    );
                })
                .toList();
    }

    private Map<Long, Long> getMessageCounts(List<Long> channelIds) {
        return threadRepository.countByChannelIdsAndThreadType(channelIds, Thread.TYPE_USER_MESSAGE).stream()
                .collect(Collectors.toMap(
                        ChannelMessageCountProjection::getChannelId,
                        ChannelMessageCountProjection::getMessageCount
                ));
    }

    // 읽음 상태가 없으면 해당 채널의 사용자 메시지 전체를 unread로 봄
    private Map<Long, Long> getUnreadCounts(List<Long> channelIds, Long workspaceMemberId) {
        return threadRepository.countUnreadByChannelIdsAndThreadType(
                        channelIds,
                        Thread.TYPE_USER_MESSAGE,
                        workspaceMemberId
                )
                .stream()
                .collect(Collectors.toMap(
                        ChannelMessageCountProjection::getChannelId,
                        ChannelMessageCountProjection::getMessageCount
                ));
    }

    private Map<Long, Thread> getLatestMessages(List<Long> channelIds) {
        return threadRepository.findLatestByChannelIdsAndThreadType(channelIds, Thread.TYPE_USER_MESSAGE).stream()
                .collect(Collectors.toMap(
                        thread -> thread.getChannel().getId(),
                        Function.identity()
                ));
    }

    private WorkspaceMember findActiveWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
