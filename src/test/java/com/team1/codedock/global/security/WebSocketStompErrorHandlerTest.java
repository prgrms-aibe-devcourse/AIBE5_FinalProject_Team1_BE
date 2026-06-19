package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.MimeTypeUtils;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketStompErrorHandlerTest {

    private final WebSocketStompErrorHandler errorHandler = new WebSocketStompErrorHandler();

    @Test
    @DisplayName("인증 실패 AccessDeniedException은 인증 실패 code를 포함한 STOMP ERROR로 변환한다")
    void handleAuthenticationAccessDeniedError() {
        Message<byte[]> clientMessage = clientMessage();
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                "client inbound failed",
                new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.")
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("WebSocket 인증 토큰이 필요합니다.");
        assertThat(accessor.getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        assertThat(payload)
                .contains("\"success\":false")
                .contains("\"code\":\"WS_AUTHENTICATION_FAILED\"")
                .contains("\"message\":\"WebSocket 인증 토큰이 필요합니다.\"");
    }

    @Test
    @DisplayName("권한 실패 AccessDeniedException은 권한 실패 code를 포함한 STOMP ERROR로 변환한다")
    void handleAuthorizationAccessDeniedError() {
        Message<byte[]> clientMessage = clientMessage();
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                "client inbound failed",
                new AccessDeniedException("WebSocket 구독 권한이 없습니다.")
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("WebSocket 구독 권한이 없습니다.");
        assertThat(payload)
                .contains("\"code\":\"WS_AUTHORIZATION_FAILED\"")
                .contains("\"message\":\"WebSocket 구독 권한이 없습니다.\"");
    }

    @Test
    @DisplayName("에러 메시지에 따옴표와 개행이 있어도 JSON payload를 깨뜨리지 않는다")
    void escapeErrorMessageForJsonPayload() {
        Message<byte[]> clientMessage = clientMessage();
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                "client inbound failed",
                new AccessDeniedException("WebSocket 인증 \"토큰\"\n만료")
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(payload)
                .contains("WebSocket 인증 \\\"토큰\\\"\\n만료")
                .doesNotContain("WebSocket 인증 \"토큰\"\n만료");
    }

    private Message<byte[]> clientMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
