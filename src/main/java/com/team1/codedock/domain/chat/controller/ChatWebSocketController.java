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
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final ThreadReplyService threadReplyService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/channels/{channelId}/messages")
    public void createChannelMessage(
            @DestinationVariable Long channelId,
            Principal principal,
            @Valid ChannelMessageCreateRequest request
    ) {
        Long userId = getCurrentUserId(principal);
        ChannelMessageResponse response = chatMessageService.createChannelMessage(channelId, userId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/events",
                ChatEventResponse.of(ChatEventType.MESSAGE_CREATED, response)
        );
    }

    @MessageMapping("/threads/{threadId}/replies")
    public void createThreadReply(
            @DestinationVariable Long threadId,
            Principal principal,
            @Valid ThreadReplyWebSocketCreateRequest request
    ) {
        Long userId = getCurrentUserId(principal);
        ThreadReplyResponse response = threadReplyService.createReply(
                threadId,
                userId,
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
            Principal principal,
            @Valid TypingEventRequest request
    ) {
        Long userId = getCurrentUserId(principal);
        TypingEventResponse response = chatMessageService.createTypingEventResponse(channelId, userId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/typing",
                ChatEventResponse.of(ChatEventType.TYPING, response)
        );
    }

    private Long getCurrentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
