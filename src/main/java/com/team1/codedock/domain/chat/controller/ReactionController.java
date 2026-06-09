package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ReactionToggleRequest;
import com.team1.codedock.domain.chat.dto.ReactionToggleResponse;
import com.team1.codedock.domain.chat.service.ReactionService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/channels/{channelId}/reactions")
public class ReactionController {

    private final ReactionService reactionService;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/toggle")
    public ApiResponse<ReactionToggleResponse> toggleReaction(
            @PathVariable Long channelId,
            @Valid @RequestBody ReactionToggleRequest request
    ) {
        ReactionToggleResponse response = reactionService.toggleReaction(channelId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/events",
                ChatEventResponse.of(ChatEventType.REACTION_UPDATED, response)
        );

        return ApiResponse.ok(response);
    }
}
