package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketPresenceTrackerTest {

    private static final Object SOURCE = new Object();
    private static final String PRESENCE_DEST = "/topic/workspaces/1/presence";

    @Mock
    private WorkspaceService workspaceService;

    @InjectMocks
    private WebSocketPresenceTracker tracker;

    @Test
    @DisplayName("presence 토픽 첫 구독이면 고른 상태 브로드캐스트 + 스냅샷 전송, offline은 안 보낸다")
    void subscribeBroadcastsOnlineAndSnapshot() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        verify(workspaceService).broadcastChosenPresence(1L, 10L);
        verify(workspaceService).sendPresenceSnapshot(eq(1L), eq("alice"), eq(Set.of(10L)));
        verify(workspaceService, never()).broadcastPresence(any(), any(), any());
    }

    @Test
    @DisplayName("presence가 아닌 경로 구독은 무시한다")
    void ignoresNonPresenceSubscription() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", "/topic/channels/5/events", user));

        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("마지막 세션이 끊기면 offline을 브로드캐스트한다")
    void disconnectBroadcastsOffline() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        tracker.onDisconnect(disconnectEvent("s1"));

        verify(workspaceService).broadcastPresence(1L, 10L, "offline");
    }

    @Test
    @DisplayName("멀티 세션: 한 세션만 끊기면 offline을 보내지 않고, 모두 끊겨야 보낸다")
    void multiSessionOffline() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));
        tracker.onConnected(connectedEvent("s2", user));
        tracker.onSubscribe(subscribeEvent("s2", PRESENCE_DEST, user));

        tracker.onDisconnect(disconnectEvent("s1"));
        verify(workspaceService, never()).broadcastPresence(any(), any(), eq("offline"));

        tracker.onDisconnect(disconnectEvent("s2"));
        verify(workspaceService).broadcastPresence(1L, 10L, "offline");
    }

    @Test
    @DisplayName("같은 세션이 같은 워크스페이스를 재구독해도 online 브로드캐스트는 1회, 스냅샷은 매번")
    void resubscribeDoesNotDoubleCount() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        verify(workspaceService, times(1)).broadcastChosenPresence(1L, 10L);
        verify(workspaceService, times(2)).sendPresenceSnapshot(eq(1L), eq("alice"), any());
    }

    @Test
    @DisplayName("알 수 없는 세션 disconnect는 아무 것도 하지 않는다")
    void disconnectUnknownSession() {
        tracker.onDisconnect(disconnectEvent("ghost"));

        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("onConnected 없이 구독→끊김이어도 offline 처리된다(누수 방지)")
    void disconnectWorksWithoutConnectedEvent() {
        Principal user = principal(10L, "alice");
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        tracker.onDisconnect(disconnectEvent("s1"));

        verify(workspaceService).broadcastPresence(1L, 10L, "offline");
    }

    // --- event/principal builders ---

    private static Principal principal(long userId, String name) {
        CustomUserDetails details = mock(CustomUserDetails.class);
        lenient().when(details.getUserId()).thenReturn(userId);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(details);
        lenient().when(auth.getName()).thenReturn(name);
        return auth;
    }

    private static SessionConnectedEvent connectedEvent(String sessionId, Principal user) {
        return new SessionConnectedEvent(SOURCE, message(StompCommand.CONNECTED, sessionId, null, user), user);
    }

    private static SessionSubscribeEvent subscribeEvent(String sessionId, String destination, Principal user) {
        return new SessionSubscribeEvent(SOURCE, message(StompCommand.SUBSCRIBE, sessionId, destination, user), user);
    }

    private static SessionDisconnectEvent disconnectEvent(String sessionId) {
        return new SessionDisconnectEvent(
                SOURCE,
                message(StompCommand.DISCONNECT, sessionId, null, null),
                sessionId,
                CloseStatus.NORMAL
        );
    }

    private static Message<byte[]> message(StompCommand command, String sessionId, String destination, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (sessionId != null) {
            accessor.setSessionId(sessionId);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
