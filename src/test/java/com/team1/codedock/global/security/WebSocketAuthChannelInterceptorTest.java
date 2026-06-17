package com.team1.codedock.global.security;

import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
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
import java.util.Optional;

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
    private ChannelRepository channelRepository;

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

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
    @DisplayName("CONNECT가 아닌 SEND 메시지는 JWT 재검증 없이 통과한다")
    void preSendWithSendMessage() {
        Message<?> message = stompMessage(StompCommand.SEND, null);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtProvider, userDetailsService, channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("채널 이벤트 구독은 해당 워크스페이스 활성 멤버만 허용한다")
    void subscribeChannelEventsWithWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/channels/10/events", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("채널 typing 구독도 해당 워크스페이스 활성 멤버만 허용한다")
    void subscribeChannelTypingWithWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/channels/10/typing", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("스레드 이벤트 구독은 스레드가 속한 워크스페이스 활성 멤버만 허용한다")
    void subscribeThreadEventsWithWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/threads/20/events", authenticatedPrincipal(1L));

        when(threadRepository.findWorkspaceIdById(20L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
    }

    @Test
    @DisplayName("개인 알림과 에러 큐 구독은 인증 사용자면 허용한다")
    void subscribePersonalDestinations() {
        Message<?> notificationMessage = subscribeMessage("/user/queue/notifications", authenticatedPrincipal(1L));
        Message<?> errorMessage = subscribeMessage("/user/queue/errors", authenticatedPrincipal(1L));

        assertThat(interceptor.preSend(notificationMessage, messageChannel)).isSameAs(notificationMessage);
        assertThat(interceptor.preSend(errorMessage, messageChannel)).isSameAs(errorMessage);

        verifyNoInteractions(channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("비소속 워크스페이스 채널 구독은 거부한다")
    void subscribeChannelEventsWithoutWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/channels/10/events", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(0L);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 권한이 없습니다.");
    }

    @Test
    @DisplayName("존재하지 않는 채널 구독은 거부한다")
    void subscribeMissingChannel() {
        Message<?> message = subscribeMessage("/topic/channels/10/events", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");
    }

    @Test
    @DisplayName("허용되지 않은 topic 구독은 거부한다")
    void subscribeUnknownDestination() {
        Message<?> message = subscribeMessage("/topic/unknown", authenticatedPrincipal(1L));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");
    }

    @Test
    @DisplayName("인증 Principal이 없는 구독은 거부한다")
    void subscribeWithoutPrincipal() {
        Message<?> message = subscribeMessage("/topic/channels/10/events", null);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");
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
        when(userDetailsService.loadUserById(userId)).thenThrow(new IllegalArgumentException("unknown user"));

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

    private static Message<?> subscribeMessage(String destination, Authentication authentication) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Authentication authenticatedPrincipal(Long userId) {
        when(userDetails.getUserId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, null);
    }
}
