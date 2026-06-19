package com.team1.codedock.global.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

@Component
public class WebSocketStompErrorHandler extends StompSubProtocolErrorHandler {

    private static final String AUTHENTICATION_FAILED_CODE = "WS_AUTHENTICATION_FAILED";
    private static final String AUTHORIZATION_FAILED_CODE = "WS_AUTHORIZATION_FAILED";

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = unwrap(ex);
        if (cause instanceof AccessDeniedException accessDeniedException) {
            return createAccessDeniedErrorMessage(accessDeniedException);
        }

        return super.handleClientMessageProcessingError(clientMessage, ex);
    }

    private Throwable unwrap(Throwable ex) {
        if (ex instanceof MessageDeliveryException && ex.getCause() != null) {
            return ex.getCause();
        }
        return ex;
    }

    private Message<byte[]> createAccessDeniedErrorMessage(AccessDeniedException exception) {
        String message = exception.getMessage();
        String code = isAuthenticationFailure(message) ? AUTHENTICATION_FAILED_CODE : AUTHORIZATION_FAILED_CODE;

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setContentType(MimeTypeUtils.APPLICATION_JSON);
        accessor.setLeaveMutable(true);

        // STOMP ERROR 프레임은 연결을 닫기 때문에, 클라이언트가 원인을 구분할 수 있도록 code를 함께 내려줌.
        String payload = """
                {"success":false,"code":"%s","message":"%s"}\
                """.formatted(code, escapeJson(message));

        return MessageBuilder.createMessage(payload.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }

    private boolean isAuthenticationFailure(String message) {
        return message != null && (message.contains("인증") || message.contains("토큰"));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
