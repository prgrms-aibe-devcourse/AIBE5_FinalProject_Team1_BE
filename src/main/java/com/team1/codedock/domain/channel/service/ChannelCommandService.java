package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
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

    private final ChannelRepository channelRepository;
    private final WorkspaceRepository workspaceRepository;

    public ChannelListResponse createChannel(Long workspaceId, ChannelCreateRequest request) {
        Workspace workspace = findWorkspace(workspaceId);
        String name = normalizeName(request.name());
        validateNewChannelName(workspaceId, name);

        Channel channel = Channel.createCustom(workspace, name, request.description());
        return ChannelListResponse.from(channelRepository.save(channel));
    }

    public ChannelListResponse updateChannel(Long workspaceId, Long channelId, ChannelUpdateRequest request) {
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        validateEditableChannel(channel);
        String name = normalizeName(request.name());
        validateUpdatedChannelName(workspaceId, channelId, name);

        channel.update(name, request.description());
        return ChannelListResponse.from(channel);
    }

    public void deleteChannel(Long workspaceId, Long channelId) {
        Channel channel = findWorkspaceChannel(workspaceId, channelId);
        if (!channel.isDeletable()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Channel cannot be deleted.");
        }
        channelRepository.delete(channel);
    }

    private Workspace findWorkspace(Long workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));
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
}
