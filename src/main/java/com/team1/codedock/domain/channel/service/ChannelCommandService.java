package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ChannelReadStatusRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChannelCommandService {

    private static final String AUTHORITY_OWNER = "owner";
    private static final String AUTHORITY_ADMIN = "admin";

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ThreadRepository threadRepository;
    private final ChannelReadStatusRepository channelReadStatusRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final GithubIssueRepository githubIssueRepository;

    public ChannelListResponse createChannel(Long workspaceId, Long userId, ChannelCreateRequest request) {
        validateChannelManager(workspaceId, userId);
        Workspace workspace = findWorkspace(workspaceId);
        String name = normalizeName(request.name());
        validateNewChannelName(workspaceId, name);

        Channel channel = Channel.createCustom(workspace, name, request.description());
        return ChannelListResponse.from(channelRepository.save(channel));
    }

    public ChannelListResponse updateChannel(
            Long workspaceId,
            Long channelId,
            Long userId,
            ChannelUpdateRequest request
    ) {
        validateChannelManager(workspaceId, userId);
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        validateEditableChannel(channel);
        String name = normalizeName(request.name());
        validateUpdatedChannelName(workspaceId, channelId, name);

        channel.update(name, request.description());
        return ChannelListResponse.from(channel);
    }

    public void deleteChannel(Long workspaceId, Long channelId, Long userId) {
        validateChannelManager(workspaceId, userId);
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        validateDeletableChannel(channel);
        validateNoChannelReferences(channelId);
        // read status는 사용자가 채널에 진입하면 생기는 파생 상태라 채널 삭제 시 함께 정리함
        channelReadStatusRepository.deleteAllByChannel_Id(channelId);
        channelRepository.delete(channel);
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private void validateChannelManager(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        var member = workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        if (!canManageChannel(member.getAuthority())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private boolean canManageChannel(String authority) {
        return AUTHORITY_OWNER.equals(authority) || AUTHORITY_ADMIN.equals(authority);
    }

    private Channel findWorkspaceChannel(Long workspaceId, Long channelId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHANNEL_NOT_FOUND));
        if (!channel.getWorkspace().getId().equals(workspaceId)) {
            throw new BusinessException(ErrorCode.CHANNEL_NOT_FOUND);
        }
        return channel;
    }

    private String normalizeName(String name) {
        return name.trim();
    }

    private void validateNewChannelName(Long workspaceId, String name) {
        if (channelRepository.existsByWorkspace_IdAndNameIgnoreCase(workspaceId, name)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel name already exists in workspace.");
        }
    }

    private void validateUpdatedChannelName(Long workspaceId, Long channelId, String name) {
        if (channelRepository.existsByWorkspace_IdAndNameIgnoreCaseAndIdNot(workspaceId, name, channelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel name already exists in workspace.");
        }
    }

    private void validateEditableChannel(Channel channel) {
        if (!channel.isDeletable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel cannot be modified.");
        }
    }

    private void validateDeletableChannel(Channel channel) {
        if (!channel.isDeletable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel cannot be deleted.");
        }
    }

    private void validateNoChannelReferences(Long channelId) {
        if (threadRepository.existsByChannel_Id(channelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel with messages cannot be deleted.");
        }
        if (githubPullRequestRepository.existsByChannel_Id(channelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel with pull requests cannot be deleted.");
        }
        if (githubIssueRepository.existsByChannel_Id(channelId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel with issues cannot be deleted.");
        }
    }
}
