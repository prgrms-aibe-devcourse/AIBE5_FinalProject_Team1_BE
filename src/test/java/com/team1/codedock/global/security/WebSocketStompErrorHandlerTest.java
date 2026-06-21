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
    @DisplayName("래핑되지 않은 인증 실패 AccessDeniedException도 인증 실패 code로 변환한다")
    void handleDirectAuthenticationAccessDeniedError() {
        Message<byte[]> clientMessage = clientMessage();
        AccessDeniedException exception = new AccessDeniedException("유효하지 않은 WebSocket 인증 토큰입니다.");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getMessage()).isEqualTo("유효하지 않은 WebSocket 인증 토큰입니다.");
        assertThat(payload)
                .contains("\"code\":\"WS_AUTHENTICATION_FAILED\"")
                .contains("\"message\":\"유효하지 않은 WebSocket 인증 토큰입니다.\"");
    }

    @Test
    @DisplayName("JWT 만료처럼 영문으로 내려온 토큰 오류도 인증 실패 code로 변환한다")
    void handleEnglishJwtExpiredAccessDeniedError() {
        Message<byte[]> clientMessage = clientMessage();
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                "client inbound failed",
                new AccessDeniedException("JWT expired at 2026-06-19T00:00:00Z")
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(payload)
                .contains("\"code\":\"WS_AUTHENTICATION_FAILED\"")
                .contains("\"message\":\"JWT expired at 2026-06-19T00:00:00Z\"");
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
    @DisplayName("권한 실패 메시지는 토큰 재발급 대상이 아닌 권한 실패 code로 유지한다")
    void handleMembershipAccessDeniedErrorAsAuthorizationFailure() {
        Message<byte[]> clientMessage = clientMessage();
        AccessDeniedException exception = new AccessDeniedException("워크스페이스 활성 멤버만 채널을 구독할 수 있습니다.");

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(payload)
                .contains("\"code\":\"WS_AUTHORIZATION_FAILED\"")
                .contains("\"message\":\"워크스페이스 활성 멤버만 채널을 구독할 수 있습니다.\"")
                .doesNotContain("WS_AUTHENTICATION_FAILED");
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

    @Test
    @DisplayName("에러 메시지에 백슬래시가 있어도 JSON payload를 깨뜨리지 않는다")
    void escapeBackslashForJsonPayload() {
        Message<byte[]> clientMessage = clientMessage();
        MessageDeliveryException exception = new MessageDeliveryException(
                clientMessage,
                "client inbound failed",
                new AccessDeniedException("WebSocket 인증 토큰 경로 C:\\temp\\token")
        );

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(payload)
                .contains("C:\\\\temp\\\\token")
                .doesNotContain("C:\\temp\\token\"");
    }

    @Test
    @DisplayName("에러 메시지가 비어 있어도 JSON 형태의 권한 실패 payload를 반환한다")
    void handleBlankAccessDeniedMessage() {
        Message<byte[]> clientMessage = clientMessage();
        AccessDeniedException exception = new AccessDeniedException(null);

        Message<byte[]> result = errorHandler.handleClientMessageProcessingError(clientMessage, exception);

        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(result);
        String payload = new String(result.getPayload(), StandardCharsets.UTF_8);

        assertThat(accessor.getCommand()).isEqualTo(StompCommand.ERROR);
        assertThat(accessor.getContentType()).isEqualTo(MimeTypeUtils.APPLICATION_JSON);
        assertThat(payload)
                .contains("\"code\":\"WS_AUTHORIZATION_FAILED\"")
                .contains("\"message\":\"\"");
    }

    private Message<byte[]> clientMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
