package com.team1.codedock.global.security;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String LOWERCASE_AUTHORIZATION_HEADER = "authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = extractBearerToken(accessor);
        if (!jwtProvider.validateAccessToken(token)) {
            throw new AccessDeniedException("유효하지 않은 WebSocket 인증 토큰입니다.");
        }

        CustomUserDetails userDetails = loadUserDetails(token);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails,
                null,
                userDetails.getAuthorities()
        );

        // STOMP 세션에 인증 사용자 심어둠. 이후 /user 목적지와 Principal 조회에서 사용됨
        accessor.setUser(authentication);
        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = getAuthorizationHeader(accessor);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private String getAuthorizationHeader(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        return accessor.getFirstNativeHeader(LOWERCASE_AUTHORIZATION_HEADER);
    }

    private CustomUserDetails loadUserDetails(String token) {
        try {
            Long userId = jwtProvider.getUserId(token);
            return userDetailsService.loadUserById(userId);
        } catch (RuntimeException e) {
            throw new AccessDeniedException("WebSocket 인증 사용자를 찾을 수 없습니다.", e);
        }
    }
}
