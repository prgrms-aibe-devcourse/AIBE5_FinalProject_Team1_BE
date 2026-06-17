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
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
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
        // CONNECT 인증에서 설정된 Principal 기준으로 메시지 작성자 판별함
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
        // 답글 작성자도 요청 body가 아니라 인증 사용자 기준으로 처리함
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
        // typing 이벤트도 서버에서 현재 멤버를 찾아 payload를 만듦
        Long userId = getCurrentUserId(principal);
        TypingEventResponse response = chatMessageService.createTypingEventResponse(channelId, userId, request);

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channelId + "/typing",
                ChatEventResponse.of(ChatEventType.TYPING, response)
        );
    }

    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        return ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    @MessageExceptionHandler(org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class)
    @SendToUser("/queue/errors")
    public ApiResponse<Void> handleValidationException() {
        return ApiResponse.fail(ErrorCode.INVALID_INPUT.getCode(), "WebSocket 요청 payload가 올바르지 않습니다.");
    }

    private Long getCurrentUserId(Principal principal) {
        // WebSocketAuthChannelInterceptor가 Authentication Principal을 넣은 상태여야 함
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
