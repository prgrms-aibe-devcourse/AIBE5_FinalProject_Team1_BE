package com.team1.codedock.domain.channel.controller;

import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.service.ChannelCommandService;
import com.team1.codedock.domain.channel.service.ChannelQueryService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/channels")
public class ChannelController {

    private final ChannelQueryService channelQueryService;
    private final ChannelCommandService channelCommandService;

    @GetMapping
    public ApiResponse<List<ChannelListResponse>> getChannels(
            @PathVariable Long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(channelQueryService.getChannels(workspaceId, userId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelListResponse> createChannel(
            @PathVariable Long workspaceId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChannelCreateRequest request
    ) {
        return ApiResponse.ok(channelCommandService.createChannel(workspaceId, userId, request));
    }

    @PatchMapping("/{channelId}")
    public ApiResponse<ChannelListResponse> updateChannel(
            @PathVariable Long workspaceId,
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChannelUpdateRequest request
    ) {
        return ApiResponse.ok(channelCommandService.updateChannel(workspaceId, channelId, userId, request));
    }

    @DeleteMapping("/{channelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChannel(
            @PathVariable Long workspaceId,
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        channelCommandService.deleteChannel(workspaceId, channelId, userId);
    }
}
