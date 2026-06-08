package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/messages")
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    @GetMapping
    public ApiResponse<List<ChannelMessageResponse>> getChannelMessages(@PathVariable Long channelId) {
        return ApiResponse.ok(chatMessageService.getChannelMessages(channelId));
    }
}
