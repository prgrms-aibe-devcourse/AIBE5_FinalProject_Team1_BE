package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelQueryService {

    private final ChannelRepository channelRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    public List<ChannelListResponse> getChannels(Long workspaceId, Long userId) {
        validateActiveWorkspaceMember(workspaceId, userId);

        return channelRepository.findAllByWorkspace_IdOrderByIdAsc(workspaceId).stream()
                .map(ChannelListResponse::from)
                .toList();
    }

    private void validateActiveWorkspaceMember(Long workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }
}
