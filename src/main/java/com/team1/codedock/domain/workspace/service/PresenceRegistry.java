package com.team1.codedock.domain.workspace.service;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 사이트에 WS로 접속해 있는 사용자(=online) 집합을 메모리에서 관리하는 단일 소스.
 * - WebSocketPresenceTracker(쓰기): 세션 연결/종료에 맞춰 increment/decrement
 * - WorkspaceService(읽기): 워크스페이스 목록의 실시간 접속 인원 계산, presence 스냅샷
 *
 * userId별 활성 세션 수를 세어, 1개 이상이면 online으로 본다(탭/워크스페이스 무관).
 * (다중 BE 인스턴스에서는 분산되지 않음 — 단일 인스턴스 가정, 필요 시 Redis로 대체)
 */
@Component
public class PresenceRegistry {

    private static final long MANUAL_ONLINE_TTL_MILLIS = 90_000L;

    private final ConcurrentMap<Long, Integer> userSessionCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, Long> manualOnlineExpiresAtByUserId = new ConcurrentHashMap<>();

    /** 세션 +1. 이 호출로 offline→online이 되면 true. */
    public boolean increment(Long userId) {
        if (userId == null) {
            return false;
        }
        int before = userSessionCounts.getOrDefault(userId, 0);
        userSessionCounts.merge(userId, 1, Integer::sum);
        return before == 0;
    }

    /** 세션 -1. 이 호출로 online→offline(0)이 되면 true. */
    public boolean decrement(Long userId) {
        if (userId == null) {
            return false;
        }
        int remaining = userSessionCounts.merge(userId, -1, Integer::sum);
        if (remaining <= 0) {
            userSessionCounts.remove(userId);
            return true;
        }
        return false;
    }

    public boolean isOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        clearExpiredManualOnline(userId);
        Integer count = userSessionCounts.get(userId);
        return (count != null && count > 0) || manualOnlineExpiresAtByUserId.containsKey(userId);
    }

    public Set<Long> onlineUserIds() {
        clearExpiredManualOnline();
        Set<Long> onlineUserIds = new HashSet<>(manualOnlineExpiresAtByUserId.keySet());
        userSessionCounts.forEach((userId, count) -> {
            if (count != null && count > 0) {
                onlineUserIds.add(userId);
            }
        });
        return onlineUserIds;
    }

    public boolean hasConnectedSession(Long userId) {
        if (userId == null) {
            return false;
        }
        Integer count = userSessionCounts.get(userId);
        return count != null && count > 0;
    }

    public boolean markOnline(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean wasOnline = isOnline(userId);
        manualOnlineExpiresAtByUserId.put(userId, System.currentTimeMillis() + MANUAL_ONLINE_TTL_MILLIS);
        return !wasOnline;
    }

    public boolean markOffline(Long userId) {
        if (userId == null) {
            return false;
        }
        boolean wasOnline = isOnline(userId);
        manualOnlineExpiresAtByUserId.remove(userId);
        return wasOnline && !isOnline(userId);
    }

    private void clearExpiredManualOnline(Long userId) {
        Long expiresAt = manualOnlineExpiresAtByUserId.get(userId);
        if (expiresAt != null && expiresAt <= System.currentTimeMillis()) {
            manualOnlineExpiresAtByUserId.remove(userId, expiresAt);
        }
    }

    private void clearExpiredManualOnline() {
        long now = System.currentTimeMillis();
        manualOnlineExpiresAtByUserId.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
