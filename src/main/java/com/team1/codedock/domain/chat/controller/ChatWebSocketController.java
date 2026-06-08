package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/channels/{channelId}/messages")
    public void createChannelMessage(
            @DestinationVariable Long channelId,
            @Valid ChannelMessageCreateRequest request
    ) {
        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/events",
                ChatEventResponse.of(ChatEventType.MESSAGE_CREATED, response)
        );
    }
}
