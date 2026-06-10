package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageUpdateRequest;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ApiResponse<List<ChannelMessageResponse>> getChannelMessages(
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return ApiResponse.ok(chatMessageService.getChannelMessages(channelId, userId, cursor, limit));
    }

    @PostMapping
    public ApiResponse<ChannelMessageResponse> createChannelMessage(
            @PathVariable Long channelId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChannelMessageRestCreateRequest request
    ) {
        return ApiResponse.ok(chatMessageService.createChannelMessage(channelId, userId, request));
    }

    @PatchMapping("/{messageId}")
    public ApiResponse<ChannelMessageResponse> updateChannelMessage(
            @PathVariable Long channelId,
            @PathVariable Long messageId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @Valid @RequestBody ChannelMessageUpdateRequest request
    ) {
        ChannelMessageResponse response = chatMessageService.updateChannelMessage(channelId, messageId, userId, request);
        broadcastChannelEvent(channelId, ChatEventType.MESSAGE_UPDATED, response);
        return ApiResponse.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<ChannelMessageResponse> deleteChannelMessage(
            @PathVariable Long channelId,
            @PathVariable Long messageId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId
    ) {
        ChannelMessageResponse response = chatMessageService.deleteChannelMessage(channelId, messageId, userId);
        broadcastChannelEvent(channelId, ChatEventType.MESSAGE_DELETED, response);
        return ApiResponse.ok(response);
    }

    private void broadcastChannelEvent(Long channelId, ChatEventType eventType, ChannelMessageResponse response) {
        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/events",
                ChatEventResponse.of(eventType, response)
        );
    }
}
