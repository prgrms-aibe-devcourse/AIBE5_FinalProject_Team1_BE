package com.team1.codedock.domain.workspace.service;

import com.team1.codedock.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks STOMP sessions and manages workspace presence based on active site connections.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceTracker {

    private static final Pattern PRESENCE_DESTINATION =
            Pattern.compile("^/topic/workspaces/(\\d+)/presence$");

    private final WorkspaceService workspaceService;
    private final PresenceRegistry presenceRegistry;

    private final ConcurrentMap<String, Long> sessionUserIds = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        registerSession(accessor.getSessionId(), resolveUserId(event.getUser()));
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || sessionId == null) {
            return;
        }
        Long userId = sessionUserIds.get(sessionId);
        if (userId == null) {
            userId = resolveUserId(event.getUser());
        }
        if (userId == null) {
            return;
        }
        // A global workspace subscription is enough to consider the user connected.
        registerSession(sessionId, userId);

        Matcher matcher = PRESENCE_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        Long workspaceId = parseId(matcher.group(1));
        if (workspaceId == null) {
            return;
        }

        final Long ws = workspaceId;
        final String recipient = principalName(event.getUser());
        // Presence subscribers additionally receive the current workspace snapshot.
        safelyRun(() -> workspaceService.sendPresenceSnapshot(ws, recipient, onlineUserIds()));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = sessionUserIds.remove(event.getSessionId());
        if (userId == null) {
            return;
        }
        if (presenceRegistry.decrement(userId)) {
            presenceRegistry.markOffline(userId);
            final Long uid = userId;
            safelyRun(() -> workspaceService.broadcastUserPresenceToAllWorkspaces(uid, false));
        }
    }

    private void registerSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        Long previous = sessionUserIds.putIfAbsent(sessionId, userId);
        if (previous != null) {
            return;
        }
        if (presenceRegistry.increment(userId)) {
            final Long uid = userId;
            safelyRun(() -> workspaceService.broadcastUserPresenceToAllWorkspaces(uid, true));
        }
    }

    private Set<Long> onlineUserIds() {
        return presenceRegistry.onlineUserIds();
    }

    private void safelyRun(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("WebSocket presence handling failed", e);
        }
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }

    private String principalName(Principal principal) {
        return principal != null ? principal.getName() : null;
    }

    private Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
