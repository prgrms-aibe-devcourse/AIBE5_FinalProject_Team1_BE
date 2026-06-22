package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.service.ChannelReadStatusService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/read")
public class ChannelReadStatusController {

    private static final String PERSONAL_NOTIFICATION_DESTINATION = "/queue/notifications";

    private final ChannelReadStatusService channelReadStatusService;
    private final SimpMessagingTemplate messagingTemplate;

    @PutMapping
    public ApiResponse<ChannelReadStatusResponse> markChannelAsRead(
            @PathVariable Long channelId
    ) {
        ChannelReadStatusResponse response =
                channelReadStatusService.markChannelAsRead(channelId, SecurityUtils.getCurrentUserId());
        ChatEventResponse<ChannelReadStatusResponse> event =
                ChatEventResponse.of(ChatEventType.CHANNEL_READ_STATUS_UPDATED, response);

        // 읽음 상태는 사용자별 데이터라 현재 사용자의 여러 세션에만 동기화함
        messagingTemplate.convertAndSendToUser(
                SecurityUtils.getCurrentUserDestinationKey(),
                PERSONAL_NOTIFICATION_DESTINATION,
                event
        );
        return ApiResponse.ok(response);
    }
}
