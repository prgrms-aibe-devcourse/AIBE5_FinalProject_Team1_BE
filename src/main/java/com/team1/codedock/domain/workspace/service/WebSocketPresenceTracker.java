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
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 세션 생명주기를 추적해 워크스페이스 presence의 "실시간 연결 여부"를 관리한다.
 * - preferences(WorkspaceMemberPreferences)는 사용자가 고른 상태(active/away/busy/offline)를 저장하고,
 * - 본 트래커는 "현재 접속 세션이 있는지"(연결 여부)를 관리한다.
 *
 * 동작:
 * - 사용자가 워크스페이스 presence 토픽을 구독하면(=입장/재연결) 현재 멤버 presence 스냅샷을 그 사용자에게만 전송하고,
 *   처음 온라인이 된 경우(0→1 세션) 다른 멤버에게 고른 상태를 브로드캐스트한다.
 * - 세션이 끊기면(정상/비정상 모두) 같은 (workspace,user)의 다른 세션이 없을 때 offline을 브로드캐스트한다.
 *   → 탭 강제 종료/네트워크 끊김도 SessionDisconnectEvent로 감지되어 다른 멤버 화면에 offline로 반영된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceTracker {

    private static final Pattern PRESENCE_DESTINATION =
            Pattern.compile("^/topic/workspaces/(\\d+)/presence$");

    private final WorkspaceService workspaceService;

    private final ConcurrentMap<String, Long> sessionUserIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<Long>> sessionWorkspaceIds = new ConcurrentHashMap<>();
    // "workspaceId:userId" -> 해당 (workspace,user)의 활성 세션 수 (멀티탭/멀티세션 고려)
    private final ConcurrentMap<String, Integer> onlineSessionCounts = new ConcurrentHashMap<>();

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        Long userId = resolveUserId(event.getUser());
        if (sessionId != null && userId != null) {
            sessionUserIds.put(sessionId, userId);
        }
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
        // onConnected가 유실/경합되더라도 disconnect 시 userId를 찾아 offline 처리할 수 있게 여기서도 보관함.
        sessionUserIds.putIfAbsent(sessionId, userId);

        final Long wsId = workspaceId;
        final Long uid = userId;

        boolean firstForSession = sessionWorkspaceIds
                .computeIfAbsent(sessionId, key -> ConcurrentHashMap.newKeySet())
                .add(wsId);

        String recipientName = principalName(event.getUser());
        if (firstForSession) {
            int before = onlineSessionCounts.getOrDefault(key(wsId, uid), 0);
            onlineSessionCounts.merge(key(wsId, uid), 1, Integer::sum);
            // 0→1: 이 사용자가 막 온라인이 됐으므로 다른 멤버에게 고른 상태를 알림(재연결 복원 포함)
            if (before == 0) {
                safelyRun(() -> workspaceService.broadcastChosenPresence(wsId, uid));
            }
        }
        // 새 구독자에게 현재 워크스페이스 presence 스냅샷 전송(이미 접속 중이던 멤버 상태를 즉시 반영)
        safelyRun(() -> workspaceService.sendPresenceSnapshot(wsId, recipientName, onlineUserIds(wsId)));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        Long userId = sessionUserIds.remove(sessionId);
        Set<Long> workspaceIds = sessionWorkspaceIds.remove(sessionId);
        if (userId == null || workspaceIds == null) {
            return;
        }
        for (Long workspaceId : workspaceIds) {
            int remaining = onlineSessionCounts.merge(key(workspaceId, userId), -1, Integer::sum);
            if (remaining <= 0) {
                onlineSessionCounts.remove(key(workspaceId, userId));
                final Long ws = workspaceId;
                final Long uid = userId;
                safelyRun(() -> workspaceService.broadcastPresence(ws, uid, "offline"));
            }
        }
    }

    private Set<Long> onlineUserIds(Long workspaceId) {
        String prefix = workspaceId + ":";
        Set<Long> ids = new HashSet<>();
        onlineSessionCounts.forEach((key, count) -> {
            if (count != null && count > 0 && key.startsWith(prefix)) {
                Long uid = parseId(key.substring(prefix.length()));
                if (uid != null) {
                    ids.add(uid);
                }
            }
        });
        return ids;
    }

    private void safelyRun(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            // presence 브로드캐스트 실패가 세션 처리 자체를 막지 않도록 격리함
            log.warn("WebSocket presence 처리 중 오류", e);
        }
    }

    private String key(Long workspaceId, Long userId) {
        return workspaceId + ":" + userId;
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
