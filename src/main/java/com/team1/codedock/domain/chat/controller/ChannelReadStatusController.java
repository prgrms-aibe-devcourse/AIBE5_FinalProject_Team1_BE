package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.service.ChannelReadStatusService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/read")
public class ChannelReadStatusController {

    private final ChannelReadStatusService channelReadStatusService;

    // 현재 임시 사용자 헤더 기준으로 채널 읽음 처리함
    @PutMapping
    public ApiResponse<ChannelReadStatusResponse> markChannelAsRead(
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(channelReadStatusService.markChannelAsRead(channelId, userId));
    }
}
