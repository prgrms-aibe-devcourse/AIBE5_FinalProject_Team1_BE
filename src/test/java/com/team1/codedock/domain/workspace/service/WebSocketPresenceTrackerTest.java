package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
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

    private WebSocketPresenceTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new WebSocketPresenceTracker(workspaceService, new PresenceRegistry());
    }

    @Test
    @DisplayName("First session broadcasts online")
    void firstSessionBroadcastsOnline() {
        Principal user = principal(10L, "alice");

        tracker.onConnected(connectedEvent("s1", user));

        verify(workspaceService).broadcastUserPresenceToAllWorkspaces(10L, true);
        verify(workspaceService, never()).broadcastUserPresenceToAllWorkspaces(anyLong(), eq(false));
    }

    @Test
    @DisplayName("Presence subscription sends snapshot")
    void subscribeSendsSnapshot() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));

        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        verify(workspaceService).sendPresenceSnapshot(eq(1L), eq("alice"), eq(Set.of(10L)));
    }

    @Test
    @DisplayName("Non-presence subscription registers authenticated session without snapshot")
    void nonPresenceSubscribeRegistersOnlineWithoutSnapshot() {
        Principal user = principal(10L, "alice");

        tracker.onSubscribe(subscribeEvent("s1", "/topic/channels/5/events", user));

        verify(workspaceService).broadcastUserPresenceToAllWorkspaces(10L, true);
        verify(workspaceService, never()).sendPresenceSnapshot(anyLong(), any(), any());
    }

    @Test
    @DisplayName("Last session broadcasts offline")
    void lastSessionBroadcastsOffline() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));

        tracker.onDisconnect(disconnectEvent("s1"));

        verify(workspaceService).broadcastUserPresenceToAllWorkspaces(10L, false);
    }

    @Test
    @DisplayName("Multiple sessions broadcast online once and offline on last disconnect")
    void multiSessionOnlineOnceOfflineOnLast() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onConnected(connectedEvent("s2", user));

        verify(workspaceService, times(1)).broadcastUserPresenceToAllWorkspaces(10L, true);

        tracker.onDisconnect(disconnectEvent("s1"));
        verify(workspaceService, never()).broadcastUserPresenceToAllWorkspaces(anyLong(), eq(false));

        tracker.onDisconnect(disconnectEvent("s2"));
        verify(workspaceService, times(1)).broadcastUserPresenceToAllWorkspaces(10L, false);
    }

    @Test
    @DisplayName("Connect and subscribe register the same session only once")
    void registerOncePerSession() {
        Principal user = principal(10L, "alice");
        tracker.onConnected(connectedEvent("s1", user));
        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));

        verify(workspaceService, times(1)).broadcastUserPresenceToAllWorkspaces(10L, true);
    }

    @Test
    @DisplayName("Unknown session disconnect is noop")
    void disconnectUnknownSessionNoop() {
        tracker.onDisconnect(disconnectEvent("ghost"));

        verifyNoInteractions(workspaceService);
    }

    @Test
    @DisplayName("Subscribe without connected still handles online, snapshot, and offline")
    void subscribeWithoutConnectedStillWorks() {
        Principal user = principal(10L, "alice");

        tracker.onSubscribe(subscribeEvent("s1", PRESENCE_DEST, user));
        verify(workspaceService).broadcastUserPresenceToAllWorkspaces(10L, true);
        verify(workspaceService).sendPresenceSnapshot(eq(1L), eq("alice"), eq(Set.of(10L)));

        tracker.onDisconnect(disconnectEvent("s1"));
        verify(workspaceService).broadcastUserPresenceToAllWorkspaces(10L, false);
    }

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
