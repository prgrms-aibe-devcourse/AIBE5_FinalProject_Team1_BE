package com.team1.codedock.global.security;

import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Optional;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = interceptorWithClock(Clock.systemUTC());
    }

    @Test
    @DisplayName("STOMP 헤더가 아닌 메시지는 인증/인가 처리 없이 그대로 통과한다")
    void preSendWithoutStompAccessorPassesThrough() {
        Message<?> message = MessageBuilder.withPayload("plain-message").build();

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtProvider, userDetailsService, channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("UNSUBSCRIBE 같은 비대상 command는 인증/인가 처리 없이 그대로 통과한다")
    void preSendWithUnsubscribeCommandPassesThrough() {
        Message<?> message = stompCommandMessage(StompCommand.UNSUBSCRIBE);

        Message<?> result = interceptor.preSend(message, messageChannel);

        assertThat(result).isSameAs(message);
        verifyNoInteractions(jwtProvider, userDetailsService, channelRepository, threadRepository, workspaceMemberRepository);
    }

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
    @DisplayName("CONNECT 요청은 소문자 authorization 헤더도 인증 헤더로 처리한다")
    void preSendWithLowercaseAuthorizationHeader() {
        String token = "valid-access-token";
        Long userId = 1L;
        Message<?> message = stompMessage(StompCommand.CONNECT, "authorization", "Bearer " + token);

        when(jwtProvider.validateAccessToken(token)).thenReturn(true);
        when(jwtProvider.getUserId(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();

        Message<?> result = interceptor.preSend(message, messageChannel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

        verify(jwtProvider).validateAccessToken(token);
        verify(jwtProvider).getUserId(token);
        verify(userDetailsService).loadUserById(userId);
    }

    @Test
    @DisplayName("CONNECT 요청에 대소문자 Authorization 헤더가 모두 있으면 표준 Authorization 헤더를 우선한다")
    void preSendPrefersStandardAuthorizationHeader() {
        String standardToken = "standard-token";
        String lowercaseToken = "lowercase-token";
        Long userId = 1L;
        Message<?> message = stompMessageWithHeaders(
                StompCommand.CONNECT,
                List.of(
                        new NativeHeader("Authorization", "Bearer " + standardToken),
                        new NativeHeader("authorization", "Bearer " + lowercaseToken)
                )
        );

        when(jwtProvider.validateAccessToken(standardToken)).thenReturn(true);
        when(jwtProvider.getUserId(standardToken)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(userDetails);
        doReturn(List.of(new SimpleGrantedAuthority("ROLE_USER"))).when(userDetails).getAuthorities();

        Message<?> result = interceptor.preSend(message, messageChannel);

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(result, StompHeaderAccessor.class);
        assertThat(accessor).isNotNull();
        assertThat(accessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        verify(jwtProvider).validateAccessToken(standardToken);
        verify(jwtProvider, never()).validateAccessToken(lowercaseToken);
    }

    @Test
    @DisplayName("SEND 메시지는 인증 사용자 기준으로 rate limit 안에서 통과한다")
    void preSendWithSendMessageWithinRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        }

        verifyNoInteractions(jwtProvider, userDetailsService, channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("SEND 메시지가 세션별 허용량을 초과하면 거부한다")
    void preSendWithExceededSendRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(message, messageChannel);
        }

        assertThat(interceptor.preSend(message, messageChannel)).isNull();
    }

    @Test
    @DisplayName("typing SEND는 일반 메시지 rate limit에서 제외한다")
    void typingSendMessageIsExcludedFromGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> typingMessage = sendMessage("/app/channels/10/typing", authentication, "session-1");
        Message<?> normalMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 50; i++) {
            assertThat(interceptor.preSend(typingMessage, messageChannel)).isSameAs(typingMessage);
        }

        // typing 폭주는 일반 메시지 예산을 소모하지 않아 실제 메시지 전송은 계속 가능함.
        assertThat(interceptor.preSend(normalMessage, messageChannel)).isSameAs(normalMessage);
    }

    @Test
    @DisplayName("typing SEND도 인증 Principal이 없으면 거부한다")
    void typingSendWithoutPrincipalIsRejected() {
        Message<?> message = sendMessage("/app/channels/10/typing", null, "session-1");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");
    }

    @Test
    @DisplayName("typing과 비슷하지만 다른 SEND 경로는 일반 rate limit을 적용한다")
    void typingLikeSendDestinationUsesGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/typing-extra", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        }

        assertThat(interceptor.preSend(message, messageChannel)).isNull();
    }

    @Test
    @DisplayName("일반 SEND 예산이 소진되어도 typing SEND는 drop하지 않는다")
    void typingSendPassesEvenWhenGeneralRateLimitIsExhausted() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> normalMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");
        Message<?> typingMessage = sendMessage("/app/channels/10/typing", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(normalMessage, messageChannel)).isSameAs(normalMessage);
        }

        assertThat(interceptor.preSend(normalMessage, messageChannel)).isNull();

        // 일반 메시지 제한에 걸린 세션이어도 typing 상태 이벤트는 연결 종료 없이 계속 흘려보냄.
        for (int i = 0; i < 10; i++) {
            assertThat(interceptor.preSend(typingMessage, messageChannel)).isSameAs(typingMessage);
        }
    }

    @Test
    @DisplayName("세션 id가 없는 typing SEND도 사용자 fallback rate limit 예산을 소모하지 않는다")
    void typingSendWithoutSessionDoesNotConsumeUserFallbackRateLimit() {
        Authentication authentication = authentication(1L);
        Message<?> typingMessage = sendMessage("/app/channels/10/typing", authentication, null);
        Message<?> normalMessage = sendMessage("/app/channels/10/messages", authentication, null);

        for (int i = 0; i < 50; i++) {
            assertThat(interceptor.preSend(typingMessage, messageChannel)).isSameAs(typingMessage);
        }

        assertThat(interceptor.preSend(normalMessage, messageChannel)).isSameAs(normalMessage);
    }

    @Test
    @DisplayName("숫자가 아닌 channelId의 typing SEND 경로는 일반 rate limit을 적용한다")
    void typingSendDestinationWithNonNumericChannelIdUsesGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/not-number/typing", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        }

        assertThat(interceptor.preSend(message, messageChannel)).isNull();
    }

    @Test
    @DisplayName("typing SEND 경로에 trailing slash가 붙으면 일반 rate limit을 적용한다")
    void typingSendDestinationWithTrailingSlashUsesGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/typing/", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        }

        assertThat(interceptor.preSend(message, messageChannel)).isNull();
    }

    @Test
    @DisplayName("typing SEND가 중간에 섞여도 일반 메시지 rate limit 카운트를 증가시키지 않는다")
    void typingSendBetweenNormalMessagesDoesNotConsumeGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> normalMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");
        Message<?> typingMessage = sendMessage("/app/channels/10/typing", authentication, "session-1");

        for (int i = 0; i < 19; i++) {
            assertThat(interceptor.preSend(normalMessage, messageChannel)).isSameAs(normalMessage);
            assertThat(interceptor.preSend(typingMessage, messageChannel)).isSameAs(typingMessage);
        }

        // typing이 일반 메시지 예산을 먹었다면 여기서 drop되므로, 20번째 일반 메시지까지 통과하는지 확인함.
        assertThat(interceptor.preSend(normalMessage, messageChannel)).isSameAs(normalMessage);
        assertThat(interceptor.preSend(normalMessage, messageChannel)).isNull();
    }

    @Test
    @DisplayName("typing SEND만 반복하면 rate limit 캐시 엔트리를 만들지 않는다")
    void typingOnlySendDoesNotCreateRateLimitCacheEntry() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> typingMessage = sendMessage("/app/channels/10/typing", authentication, "session-1");

        for (int i = 0; i < 100; i++) {
            assertThat(interceptor.preSend(typingMessage, messageChannel)).isSameAs(typingMessage);
        }

        clock.advance(Duration.ofMillis(30_000));

        // typing은 rate limit window를 만들지 않으므로 sweep 대상도 없어야 함.
        assertThat(interceptor.sweepExpiredCacheEntries(clock.millis())).isZero();
    }

    @Test
    @DisplayName("스레드 답글 SEND는 일반 메시지와 동일하게 rate limit을 적용한다")
    void threadReplySendUsesGeneralRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/threads/10/replies", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        }

        assertThat(interceptor.preSend(message, messageChannel)).isNull();
    }

    @Test
    @DisplayName("채널 메시지와 스레드 답글 SEND는 같은 세션 rate limit 예산을 공유한다")
    void channelMessageAndThreadReplyShareSessionRateLimitBudget() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> channelMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");
        Message<?> threadReply = sendMessage("/app/threads/20/replies", authentication, "session-1");

        for (int i = 0; i < 10; i++) {
            assertThat(interceptor.preSend(channelMessage, messageChannel)).isSameAs(channelMessage);
            assertThat(interceptor.preSend(threadReply, messageChannel)).isSameAs(threadReply);
        }

        assertThat(interceptor.preSend(channelMessage, messageChannel)).isNull();
        assertThat(interceptor.preSend(threadReply, messageChannel)).isNull();
    }

    @Test
    @DisplayName("SEND rate limit은 윈도우가 지나면 다시 허용한다")
    void sendRateLimitResetsAfterWindowExpires() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(message, messageChannel);
        }
        assertThat(interceptor.preSend(message, messageChannel)).isNull();

        clock.advance(Duration.ofMillis(10_000));

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
    }

    @Test
    @DisplayName("SEND rate limit은 윈도우 만료 직전에는 초기화하지 않는다")
    void sendRateLimitDoesNotResetBeforeWindowExpires() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(message, messageChannel);
        }

        clock.advance(Duration.ofMillis(9_999));

        assertThat(interceptor.preSend(message, messageChannel)).isNull();

        clock.advance(Duration.ofMillis(1));

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
    }

    @Test
    @DisplayName("SEND rate limit은 세션별로 분리한다")
    void sendRateLimitIsSeparatedBySession() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> firstSessionMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");
        Message<?> secondSessionMessage = sendMessage("/app/channels/10/messages", authentication, "session-2");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(firstSessionMessage, messageChannel);
        }

        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);
        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isNull();
    }

    @Test
    @DisplayName("세션 id가 없는 SEND rate limit은 사용자 기준 fallback 키로 분리한다")
    void sendRateLimitWithoutSessionUsesUserFallback() {
        Message<?> firstUserMessage = sendMessage("/app/channels/10/messages", authentication(1L), null);
        Message<?> secondUserMessage = sendMessage("/app/channels/10/messages", authentication(2L), null);

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(firstUserMessage, messageChannel);
        }

        assertThat(interceptor.preSend(secondUserMessage, messageChannel)).isSameAs(secondUserMessage);
        assertThat(interceptor.preSend(firstUserMessage, messageChannel)).isNull();
    }

    @Test
    @DisplayName("DISCONNECT 시 세션별 SEND rate limit 상태를 정리한다")
    void disconnectClearsSendRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> sendMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");
        Message<?> disconnectMessage = disconnectMessage("session-1");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage, messageChannel);
        }
        assertThat(interceptor.preSend(sendMessage, messageChannel)).isNull();

        interceptor.preSend(disconnectMessage, messageChannel);

        assertThat(interceptor.preSend(sendMessage, messageChannel)).isSameAs(sendMessage);
    }

    @Test
    @DisplayName("SessionDisconnectEvent는 비정상 종료 세션의 SEND rate limit 상태를 정리한다")
    void sessionDisconnectEventClearsSendRateLimit() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> sendMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage, messageChannel);
        }
        assertThat(interceptor.preSend(sendMessage, messageChannel)).isNull();

        interceptor.handleSessionDisconnect(sessionDisconnectEvent("session-1"));

        assertThat(interceptor.preSend(sendMessage, messageChannel)).isSameAs(sendMessage);
    }

    @Test
    @DisplayName("세션 id가 없는 DISCONNECT는 사용자 fallback SEND rate limit을 정리하지 않는다")
    void disconnectWithoutSessionDoesNotClearUserFallbackRateLimit() {
        Message<?> sendMessage = sendMessage("/app/channels/10/messages", authentication(1L), null);
        Message<?> disconnectMessage = disconnectMessage(null);

        for (int i = 0; i < 20; i++) {
            interceptor.preSend(sendMessage, messageChannel);
        }

        interceptor.preSend(disconnectMessage, messageChannel);

        assertThat(interceptor.preSend(sendMessage, messageChannel)).isNull();
    }

    @Test
    @DisplayName("인증 Principal이 없는 SEND 메시지는 거부한다")
    void preSendWithoutPrincipal() {
        Message<?> message = sendMessage("/app/channels/10/messages", null, "session-1");

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");
    }

    @Test
    @DisplayName("CustomUserDetails가 아닌 Authentication Principal의 SEND 메시지는 거부한다")
    void preSendWithInvalidAuthenticationPrincipal() {
        Message<?> message = sendMessage(
                "/app/channels/10/messages",
                new UsernamePasswordAuthenticationToken("tester", null),
                "session-1"
        );

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");

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
    @DisplayName("스레드 이벤트 구독 대상 스레드가 없으면 거부한다")
    void subscribeMissingThread() {
        Message<?> message = subscribeMessage("/topic/threads/20/events", authenticatedPrincipal(1L));

        when(threadRepository.findWorkspaceIdById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");

        verify(threadRepository).findWorkspaceIdById(20L);
        verifyNoInteractions(channelRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("비소속 워크스페이스 스레드 이벤트 구독은 거부한다")
    void subscribeThreadEventsWithoutWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/threads/20/events", authenticatedPrincipal(1L));

        when(threadRepository.findWorkspaceIdById(20L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(0L);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 권한이 없습니다.");

        verify(threadRepository).findWorkspaceIdById(20L);
        verify(workspaceMemberRepository).countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
        verifyNoInteractions(channelRepository);
    }

    @Test
    @DisplayName("같은 destination 반복 구독은 workspace 조회와 멤버십 검증 캐시를 사용한다")
    void subscribeSameDestinationUsesCache() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(1))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("같은 세션의 같은 워크스페이스 구독은 멤버십 검증 캐시를 공유한다")
    void subscribeSameWorkspaceUsesMembershipCache() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> firstMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> secondMessage = subscribeMessage("/topic/channels/11/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(channelRepository.findWorkspaceIdById(11L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(firstMessage, messageChannel)).isSameAs(firstMessage);
        assertThat(interceptor.preSend(secondMessage, messageChannel)).isSameAs(secondMessage);

        verify(channelRepository).findWorkspaceIdById(10L);
        verify(channelRepository).findWorkspaceIdById(11L);
        verify(workspaceMemberRepository, times(1))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("구독 권한 캐시는 세션 간 공유하지 않는다")
    void subscribeAuthorizationCacheIsSeparatedBySession() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> firstSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> secondSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-2");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isSameAs(firstSessionMessage);
        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(2))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("같은 세션이라도 사용자 id가 다르면 구독 권한 캐시를 공유하지 않는다")
    void subscribeAuthorizationCacheIsSeparatedByUser() {
        Message<?> firstUserMessage = subscribeMessage("/topic/channels/10/events", authentication(1L), "session-1");
        Message<?> secondUserMessage = subscribeMessage("/topic/channels/10/events", authentication(2L), "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 2L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(firstUserMessage, messageChannel)).isSameAs(firstUserMessage);
        assertThat(interceptor.preSend(secondUserMessage, messageChannel)).isSameAs(secondUserMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository).countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
        verify(workspaceMemberRepository).countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 2L);
    }

    @Test
    @DisplayName("구독 권한 캐시는 TTL이 지나면 다시 검증한다")
    void subscribeAuthorizationCacheExpires() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        clock.advance(Duration.ofMillis(30_000));
        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);

        verify(channelRepository, times(2)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(2))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("구독 권한 캐시는 TTL 만료 직전에는 재검증하지 않는다")
    void subscribeAuthorizationCacheDoesNotExpireBeforeTtl() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        clock.advance(Duration.ofMillis(29_999));
        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(1))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("구독 권한 캐시 TTL 이후 멤버십이 사라지면 구독을 거부한다")
    void subscribeAuthorizationCacheDeniesAfterMembershipExpires() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L, 0L);

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);
        clock.advance(Duration.ofMillis(30_000));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 권한이 없습니다.");

        verify(channelRepository, times(2)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(2))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("DISCONNECT는 해당 세션의 구독 권한 캐시만 정리한다")
    void disconnectClearsOnlyMatchingSessionSubscribeAuthorizationCache() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> firstSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> secondSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-2");
        Message<?> disconnectFirstSession = disconnectMessage("session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isSameAs(firstSessionMessage);
        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);

        interceptor.preSend(disconnectFirstSession, messageChannel);

        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isSameAs(firstSessionMessage);
        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(3))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("SessionDisconnectEvent는 비정상 종료 세션의 구독 권한 캐시만 정리한다")
    void sessionDisconnectEventClearsOnlyMatchingSessionSubscribeAuthorizationCache() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> firstSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> secondSessionMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-2");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isSameAs(firstSessionMessage);
        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);

        interceptor.handleSessionDisconnect(sessionDisconnectEvent("session-1"));

        assertThat(interceptor.preSend(firstSessionMessage, messageChannel)).isSameAs(firstSessionMessage);
        assertThat(interceptor.preSend(secondSessionMessage, messageChannel)).isSameAs(secondSessionMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(3))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("만료 캐시 sweep은 destination, 구독 권한, SEND rate limit 엔트리를 제거한다")
    void sweepExpiredCacheEntriesRemovesExpiredCaches() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> subscribeMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> sendMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(subscribeMessage, messageChannel)).isSameAs(subscribeMessage);
        assertThat(interceptor.preSend(sendMessage, messageChannel)).isSameAs(sendMessage);

        clock.advance(Duration.ofMillis(30_000));

        assertThat(interceptor.sweepExpiredCacheEntries(clock.millis())).isEqualTo(3);
        assertThat(interceptor.sweepExpiredCacheEntries(clock.millis())).isZero();
    }

    @Test
    @DisplayName("만료 캐시 sweep은 아직 유효한 엔트리를 제거하지 않는다")
    void sweepExpiredCacheEntriesKeepsFreshCaches() {
        MutableClock clock = new MutableClock();
        WebSocketAuthChannelInterceptor interceptor = interceptorWithClock(clock);
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> subscribeMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> sendMessage = sendMessage("/app/channels/10/messages", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(subscribeMessage, messageChannel)).isSameAs(subscribeMessage);
        assertThat(interceptor.preSend(sendMessage, messageChannel)).isSameAs(sendMessage);

        clock.advance(Duration.ofMillis(9_999));

        assertThat(interceptor.sweepExpiredCacheEntries(clock.millis())).isZero();
        assertThat(interceptor.preSend(subscribeMessage, messageChannel)).isSameAs(subscribeMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(1))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("구독 권한 실패 결과는 캐시하지 않는다")
    void subscribeDeniedResultIsNotCached() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(0L, 1L);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 권한이 없습니다.");

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(2))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 destination 결과는 캐시하지 않는다")
    void missingDestinationResultIsNotCached() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> message = subscribeMessage("/topic/channels/10/events", authentication, "session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.empty(), Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");

        assertThat(interceptor.preSend(message, messageChannel)).isSameAs(message);

        verify(channelRepository, times(2)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(1))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
    }

    @Test
    @DisplayName("DISCONNECT 시 세션별 구독 권한 캐시를 정리한다")
    void disconnectClearsSubscribeAuthorizationCache() {
        Authentication authentication = authenticatedPrincipal(1L);
        Message<?> subscribeMessage = subscribeMessage("/topic/channels/10/events", authentication, "session-1");
        Message<?> disconnectMessage = disconnectMessage("session-1");

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(1L);

        assertThat(interceptor.preSend(subscribeMessage, messageChannel)).isSameAs(subscribeMessage);
        interceptor.preSend(disconnectMessage, messageChannel);
        assertThat(interceptor.preSend(subscribeMessage, messageChannel)).isSameAs(subscribeMessage);

        verify(channelRepository, times(1)).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository, times(2))
                .countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
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
    @DisplayName("인증 Principal이 없는 개인 큐 구독은 거부한다")
    void subscribePersonalDestinationWithoutPrincipal() {
        Message<?> message = subscribeMessage("/user/queue/notifications", null);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");

        verifyNoInteractions(channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("개인 큐 하위의 임의 경로 구독은 허용하지 않는다")
    void subscribeUnknownPersonalDestination() {
        Message<?> message = subscribeMessage("/user/queue/notifications/other", authenticatedPrincipal(1L));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");

        verifyNoInteractions(channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("/user prefix가 없는 개인 에러 큐 구독은 허용하지 않는다")
    void subscribeErrorQueueWithoutUserPrefix() {
        Message<?> message = subscribeMessage("/queue/errors", authenticatedPrincipal(1L));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");

        verifyNoInteractions(channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("공백 destination 구독은 거부한다")
    void subscribeBlankDestination() {
        Message<?> message = subscribeMessage(" ", authenticatedPrincipal(1L));

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 경로가 필요합니다.");

        verifyNoInteractions(channelRepository, threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("Long 범위를 넘는 destination id 구독은 거부한다")
    void subscribeDestinationIdOverflow() {
        Message<?> message = subscribeMessage(
                "/topic/channels/999999999999999999999999/events",
                authenticatedPrincipal(1L)
        );

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 경로가 올바르지 않습니다.");

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
    @DisplayName("존재하지 않는 채널 typing 구독은 거부한다")
    void subscribeMissingChannelTyping() {
        Message<?> message = subscribeMessage("/topic/channels/10/typing", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("허용되지 않은 WebSocket 구독 경로입니다.");

        verify(channelRepository).findWorkspaceIdById(10L);
        verifyNoInteractions(threadRepository, workspaceMemberRepository);
    }

    @Test
    @DisplayName("비소속 워크스페이스 채널 typing 구독은 거부한다")
    void subscribeChannelTypingWithoutWorkspaceMember() {
        Message<?> message = subscribeMessage("/topic/channels/10/typing", authenticatedPrincipal(1L));

        when(channelRepository.findWorkspaceIdById(10L)).thenReturn(Optional.of(100L));
        when(workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L))
                .thenReturn(0L);

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 구독 권한이 없습니다.");

        verify(channelRepository).findWorkspaceIdById(10L);
        verify(workspaceMemberRepository).countByWorkspace_IdAndUser_IdAndIsActiveTrue(100L, 1L);
        verifyNoInteractions(threadRepository);
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
    @DisplayName("CustomUserDetails가 아닌 Authentication Principal의 구독은 거부한다")
    void subscribeWithInvalidAuthenticationPrincipal() {
        Message<?> message = subscribeMessage(
                "/topic/channels/10/events",
                new UsernamePasswordAuthenticationToken("tester", null)
        );

        assertThatThrownBy(() -> interceptor.preSend(message, messageChannel))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket 인증 사용자가 필요합니다.");

        verifyNoInteractions(jwtProvider, userDetailsService, channelRepository, threadRepository, workspaceMemberRepository);
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
    @DisplayName("CONNECT 요청의 Bearer 토큰 본문이 비어 있으면 거부한다")
    void preSendWithBlankBearerToken() {
        Message<?> message = stompMessage(StompCommand.CONNECT, "Bearer ");

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
        return stompMessage(command, "Authorization", authorization);
    }

    private static Message<?> stompMessage(StompCommand command, String headerName, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (authorization != null) {
            accessor.setNativeHeader(headerName, authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> stompMessageWithHeaders(StompCommand command, List<NativeHeader> nativeHeaders) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        nativeHeaders.forEach(header -> accessor.setNativeHeader(header.name(), header.value()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> stompCommandMessage(StompCommand command) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> subscribeMessage(String destination, Authentication authentication) {
        return subscribeMessage(destination, authentication, null);
    }

    private static Message<?> subscribeMessage(String destination, Authentication authentication, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> sendMessage(String destination, Authentication authentication, String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setLeaveMutable(true);
        accessor.setDestination(destination);
        accessor.setUser(authentication);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<?> disconnectMessage(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setLeaveMutable(true);
        accessor.setSessionId(sessionId);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static SessionDisconnectEvent sessionDisconnectEvent(String sessionId) {
        Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
        return new SessionDisconnectEvent(new Object(), message, sessionId, CloseStatus.SESSION_NOT_RELIABLE);
    }

    private Authentication authenticatedPrincipal(Long userId) {
        when(userDetails.getUserId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, null);
    }

    private Authentication authentication(Long userId) {
        CustomUserDetails principal = org.mockito.Mockito.mock(CustomUserDetails.class);
        when(principal.getUserId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private WebSocketAuthChannelInterceptor interceptorWithClock(Clock clock) {
        return new WebSocketAuthChannelInterceptor(
                jwtProvider,
                userDetailsService,
                channelRepository,
                threadRepository,
                workspaceMemberRepository,
                clock
        );
    }

    private record NativeHeader(String name, String value) {
    }

    private static class MutableClock extends Clock {

        private Instant instant = Instant.parse("2026-06-18T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
