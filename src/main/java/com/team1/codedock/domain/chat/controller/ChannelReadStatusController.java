package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.service.ChannelReadStatusService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/read")
public class ChannelReadStatusController {

    private final ChannelReadStatusService channelReadStatusService;

    @PutMapping
    public ApiResponse<ChannelReadStatusResponse> markChannelAsRead(
            @PathVariable Long channelId
    ) {
        return ApiResponse.ok(channelReadStatusService.markChannelAsRead(channelId, SecurityUtils.getCurrentUserId()));
    }
}
