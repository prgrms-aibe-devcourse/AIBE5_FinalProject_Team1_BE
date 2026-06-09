package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
