package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthChannelInterceptorTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private CustomUserDetails userDetails;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private WebSocketAuthChannelInterceptor interceptor;

    @Test
    @DisplayName("CONNECT 요청에 유효한 JWT가 있으면 Principal을 설정한다")
    void preSendWithValidConnectToken() {
        String token = "valid-access-token";
        Long userId = 1L;
        Message<?> message = stompMessage(StompCommand.CONNECT, "Bearer " + token);

        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUserId(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();

        Message<?> result = interceptor.preSend(message, messageChannel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

        Authentication authentication = (Authentication) accessor.getUser();
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("CONNECT가 아니면 JWT 검증을 하지 않는다")
    void preSendWithNonConnectMessage() {
        Message<?> message = stompMessage(StompCommand.SUBSCRIBE, null);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtProvider, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT 요청에 Authorization 헤더가 없으면 거부한다")
    void preSendWithoutAuthorizationHeader() {
        Message<?> message = stompMessage(StompCommand.CONNECT, null);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 토큰이 필요합니다.");

        verifyNoInteractions(jwtProvider, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT 요청의 Authorization 헤더가 Bearer 형식이 아니면 거부한다")
    void preSendWithInvalidAuthorizationHeaderFormat() {
        Message<?> message = stompMessage(StompCommand.CONNECT, "Token invalid-access-token");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 토큰이 필요합니다.");

        verifyNoInteractions(jwtProvider, userDetailsService);
    }

    @Test
    @DisplayName("CONNECT 요청의 JWT가 유효하지 않으면 거부한다")
    void preSendWithInvalidAccessToken() {
        String token = "invalid-access-token";
        Message<?> message = stompMessage(StompCommand.CONNECT, "Bearer " + token);

        when(jwtProvider.validateAccessToken(token)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("유효하지 않은 WebSocket 인증 토큰입니다.");

        verify(jwtProvider, never()).getUserId(token);
        verifyNoInteractions(userDetailsService);
    }

    @Test
    @DisplayName("토큰의 사용자 정보를 찾을 수 없으면 CONNECT를 거부한다")
    void preSendWithUnknownUser() {
        String token = "valid-access-token";
        Long userId = 1L;
        Message<?> message = stompMessage(StompCommand.CONNECT, "Bearer " + token);

        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUserId(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenThrow(new IllegalArgumentException("존재하지 않는 유저입니다."));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자를 찾을 수 없습니다.");
    }

    private static Message<?> stompMessage(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
