package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyWebSocketCreateRequest;
import com.team1.codedock.domain.chat.dto.TypingEventRequest;
import com.team1.codedock.domain.chat.dto.TypingEventResponse;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
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
    private final ThreadReplyService threadReplyService;
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

    @MessageMapping("/threads/{threadId}/replies")
    public void createThreadReply(
            @DestinationVariable Long threadId,
            @Valid ThreadReplyWebSocketCreateRequest request
    ) {
        ThreadReplyResponse response = threadReplyService.createReply(
                threadId,
                request.userId(),
                request.toCreateRequest()
        );

        messagingTemplate.convertAndSend(
                "/topic/threads/" + threadId + "/events",
                ChatEventResponse.of(ChatEventType.THREAD_REPLY_CREATED, response)
        );
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void sendTypingEvent(
            @DestinationVariable Long channelId,
            @Valid TypingEventRequest request
    ) {
        TypingEventResponse response = TypingEventResponse.of(channelId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/typing",
                ChatEventResponse.of(ChatEventType.TYPING, response)
        );
    }
}
