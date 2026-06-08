package com.team1.codedock.domain.channel.service;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelQueryService {

    private final ChannelRepository channelRepository;

    public List<ChannelListResponse> getChannels(Long workspaceId) {
        return channelRepository.findAllByWorkspace_IdOrderByIdAsc(workspaceId).stream()
                .map(ChannelListResponse::from)
                .toList();
    }
}
