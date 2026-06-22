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
 * STOMP 세션 생명주기를 추적해 "사이트 접속 여부" 기준으로 워크스페이스 presence를 관리한다.
 *
 * 모델: 사용자가 사이트에 WS로 접속해 있으면(세션 ≥ 1) 그 사용자가 속한 모든 워크스페이스에서 online으로 본다.
 * - 첫 세션(0→1): 그 사용자가 속한 모든 워크스페이스 presence 토픽으로 고른 상태를 브로드캐스트.
 * - 마지막 세션(1→0, 정상/비정상 종료 모두): 모든 워크스페이스로 offline 브로드캐스트.
 * - presence 토픽 구독 시: 그 워크스페이스의 현재 presence 스냅샷을 구독자에게만 전송(접속 세션 보유 여부 기준).
 *
 * preferences(WorkspaceMemberPreferences)는 "고른 상태"(active/away/busy/offline)를 저장하고,
 * 본 트래커는 "실제 접속 세션 유무"를 관리한다.
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
        Matcher matcher = PRESENCE_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return;
        }
        Long workspaceId = parseId(matcher.group(1));
        if (workspaceId == null) {
            return;
        }
        Long userId = sessionUserIds.get(sessionId);
        if (userId == null) {
            userId = resolveUserId(event.getUser());
        }
        if (userId == null) {
            return;
        }
        // onConnected가 유실/경합돼도 여기서 세션을 등록(카운트 1회 보장)
        registerSession(sessionId, userId);

        final Long ws = workspaceId;
        final String recipient = principalName(event.getUser());
        // 새 구독자에게 현재 워크스페이스 presence 스냅샷 전송(접속 세션 보유 멤버만 online)
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

    // 세션을 1회만 등록하고, 그 사용자의 첫 세션이면(0→1) 모든 워크스페이스에 online 브로드캐스트.
    private void registerSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        Long previous = sessionUserIds.putIfAbsent(sessionId, userId);
        if (previous != null) {
            return; // 이미 등록된 세션
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
            log.warn("WebSocket presence 처리 중 오류", e);
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
