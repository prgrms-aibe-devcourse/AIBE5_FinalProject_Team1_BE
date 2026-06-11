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

    // 프론트가 채널을 열었거나 마지막까지 읽었다고 판단할 때 호출하는 엔드포인트
    @PutMapping
    public ApiResponse<ChannelReadStatusResponse> markChannelAsRead(
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        return ApiResponse.ok(channelReadStatusService.markChannelAsRead(channelId, userId));
    }
}
