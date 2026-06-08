package com.team1.codedock.domain.channel.controller;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.service.ChannelQueryService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/channels")
public class ChannelController {

    private final ChannelQueryService channelQueryService;

    @GetMapping
    public ApiResponse<List<ChannelListResponse>> getChannels(@PathVariable Long workspaceId) {
        return ApiResponse.ok(channelQueryService.getChannels(workspaceId));
    }
}
