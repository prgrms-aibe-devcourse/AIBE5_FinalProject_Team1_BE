package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.lang.reflect.Method;
import java.security.Principal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ThreadReplyService threadReplyService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    @Test
    @DisplayName("Channel message WebSocket send broadcasts MESSAGE_CREATED event")
    void createChannelMessage() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                100L,
                channelId,
                10L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 8, 10, 0)
        );

        when(chatMessageService.createChannelMessage(channelId, userId, request)).thenReturn(response);

        chatWebSocketController.createChannelMessage(channelId, principal, request);

        verify(chatMessageService).createChannelMessage(channelId, userId, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/events",
                ChatEventType.MESSAGE_CREATED,
                response
        );
    }

    @Test
    @DisplayName("Channel message WebSocket send는 서비스 실패 시 broadcast하지 않는다")
    void createChannelMessageDoesNotBroadcastWhenServiceFails() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

        when(chatMessageService.createChannelMessage(channelId, userId, request))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "채널 접근 권한이 없습니다."));

        assertThatThrownBy(() -> chatWebSocketController.createChannelMessage(channelId, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(chatMessageService).createChannelMessage(channelId, userId, request);
        verifyNoInteractions(threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Channel message WebSocket send rejects missing Principal")
    void createChannelMessageWithoutPrincipal() {
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

        assertThatThrownBy(() -> chatWebSocketController.createChannelMessage(1L, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Channel message WebSocket send rejects non-authentication Principal")
    void createChannelMessageWithNonAuthenticationPrincipal() {
        Principal principal = () -> "tester";
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

        assertThatThrownBy(() -> chatWebSocketController.createChannelMessage(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Channel message WebSocket send rejects Principal without CustomUserDetails")
    void createChannelMessageWithInvalidAuthenticationPrincipal() {
        Principal principal = new UsernamePasswordAuthenticationToken("tester", null);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");

        assertThatThrownBy(() -> chatWebSocketController.createChannelMessage(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Thread reply WebSocket send broadcasts THREAD_REPLY_CREATED event")
    void createThreadReply() {
        Long threadId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");
        ThreadReplyCreateRequest serviceRequest = new ThreadReplyCreateRequest("reply");
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.createReply(eq(threadId), eq(userId), eq(serviceRequest))).thenReturn(response);

        chatWebSocketController.createThreadReply(threadId, principal, request);

        verify(threadReplyService).createReply(threadId, userId, serviceRequest);
        assertBroadcastEvent(
                "/topic/threads/" + threadId + "/events",
                ChatEventType.THREAD_REPLY_CREATED,
                response
        );
    }

    @Test
    @DisplayName("Thread reply WebSocket send는 서비스 실패 시 broadcast하지 않는다")
    void createThreadReplyDoesNotBroadcastWhenServiceFails() {
        Long threadId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");
        ThreadReplyCreateRequest serviceRequest = new ThreadReplyCreateRequest("reply");

        when(threadReplyService.createReply(eq(threadId), eq(userId), eq(serviceRequest)))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "스레드를 찾을 수 없습니다."));

        assertThatThrownBy(() -> chatWebSocketController.createThreadReply(threadId, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(threadReplyService).createReply(threadId, userId, serviceRequest);
        verifyNoInteractions(chatMessageService, messagingTemplate);
    }

    @Test
    @DisplayName("Thread reply WebSocket send rejects missing Principal")
    void createThreadReplyWithoutPrincipal() {
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");

        assertThatThrownBy(() -> chatWebSocketController.createThreadReply(1L, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Thread reply WebSocket send rejects non-authentication Principal")
    void createThreadReplyWithNonAuthenticationPrincipal() {
        Principal principal = () -> "tester";
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");

        assertThatThrownBy(() -> chatWebSocketController.createThreadReply(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Thread reply WebSocket send rejects Principal without CustomUserDetails")
    void createThreadReplyWithInvalidAuthenticationPrincipal() {
        Principal principal = new UsernamePasswordAuthenticationToken("tester", null);
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");

        assertThatThrownBy(() -> chatWebSocketController.createThreadReply(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Typing WebSocket send broadcasts TYPING event")
    void sendTypingEvent() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        TypingEventRequest request = new TypingEventRequest(true);
        TypingEventResponse response = new TypingEventResponse(channelId, 10L, "tester", true);

        when(chatMessageService.createTypingEventResponse(channelId, userId, request)).thenReturn(response);

        chatWebSocketController.sendTypingEvent(channelId, principal, request);

        verify(chatMessageService).createTypingEventResponse(channelId, userId, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/typing",
                ChatEventType.TYPING,
                response
        );
    }

    @Test
    @DisplayName("Typing WebSocket send는 서비스 실패 시 broadcast하지 않는다")
    void sendTypingEventDoesNotBroadcastWhenServiceFails() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        TypingEventRequest request = new TypingEventRequest(true);

        when(chatMessageService.createTypingEventResponse(channelId, userId, request))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN, "채널 접근 권한이 없습니다."));

        assertThatThrownBy(() -> chatWebSocketController.sendTypingEvent(channelId, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(chatMessageService).createTypingEventResponse(channelId, userId, request);
        verifyNoInteractions(threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Typing WebSocket send rejects missing Principal")
    void sendTypingEventWithoutPrincipal() {
        TypingEventRequest request = new TypingEventRequest(true);

        assertThatThrownBy(() -> chatWebSocketController.sendTypingEvent(1L, null, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Typing WebSocket send rejects non-authentication Principal")
    void sendTypingEventWithNonAuthenticationPrincipal() {
        Principal principal = () -> "tester";
        TypingEventRequest request = new TypingEventRequest(true);

        assertThatThrownBy(() -> chatWebSocketController.sendTypingEvent(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("Typing WebSocket send rejects Principal without CustomUserDetails")
    void sendTypingEventWithInvalidAuthenticationPrincipal() {
        Principal principal = new UsernamePasswordAuthenticationToken("tester", null);
        TypingEventRequest request = new TypingEventRequest(true);

        assertThatThrownBy(() -> chatWebSocketController.sendTypingEvent(1L, principal, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(chatMessageService, threadReplyService, messagingTemplate);
    }

    @Test
    @DisplayName("WebSocket BusinessException은 사용자 에러 큐 응답 payload로 변환한다")
    void handleBusinessException() {
        BusinessException exception = new BusinessException(ErrorCode.FORBIDDEN, "WebSocket 구독 권한이 없습니다.");

        ApiResponse<Void> response = chatWebSocketController.handleBusinessException(exception);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ErrorCode.FORBIDDEN.getCode());
        assertThat(response.getMessage()).isEqualTo("WebSocket 구독 권한이 없습니다.");
    }

    @Test
    @DisplayName("WebSocket validation exception은 INVALID_INPUT 응답 payload로 변환한다")
    void handleValidationException() {
        ApiResponse<Void> response = chatWebSocketController.handleValidationException();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getCode()).isEqualTo(ErrorCode.INVALID_INPUT.getCode());
        assertThat(response.getMessage()).isEqualTo("WebSocket 요청 payload가 올바르지 않습니다.");
    }

    @Test
    @DisplayName("WebSocket BusinessException handler는 발신 세션의 에러 큐로만 응답한다")
    void businessExceptionHandlerSendsToCurrentSessionOnly() throws NoSuchMethodException {
        Method method = ChatWebSocketController.class.getDeclaredMethod("handleBusinessException", BusinessException.class);

        SendToUser sendToUser = method.getAnnotation(SendToUser.class);
        MessageExceptionHandler exceptionHandler = method.getAnnotation(MessageExceptionHandler.class);

        assertThat(sendToUser).isNotNull();
        assertThat(sendToUser.value()).containsExactly("/queue/errors");
        assertThat(sendToUser.broadcast()).isFalse();
        assertThat(exceptionHandler).isNotNull();
        assertThat(exceptionHandler.value()).containsExactly(BusinessException.class);
    }

    @Test
    @DisplayName("WebSocket validation handler는 발신 세션의 에러 큐로만 응답한다")
    void validationExceptionHandlerSendsToCurrentSessionOnly() throws NoSuchMethodException {
        Method method = ChatWebSocketController.class.getDeclaredMethod("handleValidationException");

        SendToUser sendToUser = method.getAnnotation(SendToUser.class);
        MessageExceptionHandler exceptionHandler = method.getAnnotation(MessageExceptionHandler.class);

        assertThat(sendToUser).isNotNull();
        assertThat(sendToUser.value()).containsExactly("/queue/errors");
        assertThat(sendToUser.broadcast()).isFalse();
        assertThat(exceptionHandler).isNotNull();
        assertThat(exceptionHandler.value())
                .containsExactly(org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class);
    }

    private void assertBroadcastEvent(
            String destination,
            ChatEventType expectedType,
            Object expectedPayload
    ) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);

        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(expectedType);
        assertThat(event.payload()).isEqualTo(expectedPayload);
    }

    private static Principal principal(Long userId) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, null);
    }
}
