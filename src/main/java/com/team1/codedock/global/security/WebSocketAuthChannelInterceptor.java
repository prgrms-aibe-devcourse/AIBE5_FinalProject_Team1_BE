package com.team1.codedock.global.security;

import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String LOWERCASE_AUTHORIZATION_HEADER = "authorization";
    private static final String ACCESS_TOKEN_HEADER = "access_token";
    private static final String TOKEN_HEADER = "token";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PERSONAL_NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final String PERSONAL_ERROR_DESTINATION = "/user/queue/errors";
    private static final String PERSONAL_WORKSPACE_DESTINATION = "/user/queue/workspace";
    private static final int SEND_RATE_LIMIT_CAPACITY = 20;
    private static final long SEND_RATE_LIMIT_WINDOW_MILLIS = 10_000L;
    private static final long SUBSCRIBE_AUTH_CACHE_TTL_MILLIS = 30_000L;
    private static final long CACHE_SWEEP_INTERVAL_MILLIS = 60_000L;
    private static final String SESSION_CACHE_KEY_PREFIX = "session:";
    private static final Pattern CHANNEL_EVENTS_DESTINATION =
            Pattern.compile("^/topic/channels/(\\d+)/events$");
    private static final Pattern CHANNEL_LEGACY_EVENTS_DESTINATION =
            Pattern.compile("^/topic/channels/(\\d+)$");
    private static final Pattern CHANNEL_TYPING_DESTINATION =
            Pattern.compile("^/topic/channels/(\\d+)/typing$");
    private static final Pattern CHANNEL_TYPING_SEND_DESTINATION =
            Pattern.compile("^/app/channels/\\d+/typing$");
    private static final Pattern THREAD_EVENTS_DESTINATION =
            Pattern.compile("^/topic/threads/(\\d+)/events$");
    private static final Pattern THREAD_LEGACY_EVENTS_DESTINATION =
            Pattern.compile("^/topic/threads/(\\d+)$");
    private static final Pattern WORKSPACE_PRESENCE_DESTINATION =
            Pattern.compile("^/topic/workspaces/(\\d+)/presence$");
    private static final Pattern WORKSPACE_MEMBERS_DESTINATION =
            Pattern.compile("^/topic/workspaces/(\\d+)/members$");

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ChannelRepository channelRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ConcurrentMap<String, SendRateLimitWindow> sendRateLimitWindows = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SubscriptionWorkspaceCacheEntry> subscriptionWorkspaceCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, SubscribeAuthorizationCacheEntry> subscribeAuthorizationCache = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public WebSocketAuthChannelInterceptor(
            JwtProvider jwtProvider,
            CustomUserDetailsService userDetailsService,
            ChannelRepository channelRepository,
            ThreadRepository threadRepository,
            WorkspaceMemberRepository workspaceMemberRepository
    ) {
        this(
                jwtProvider,
                userDetailsService,
                channelRepository,
                threadRepository,
                workspaceMemberRepository,
                Clock.systemUTC()
        );
    }

    WebSocketAuthChannelInterceptor(
            JwtProvider jwtProvider,
            CustomUserDetailsService userDetailsService,
            ChannelRepository channelRepository,
            ThreadRepository threadRepository,
            WorkspaceMemberRepository workspaceMemberRepository,
            Clock clock
    ) {
        this.jwtProvider = jwtProvider;
        this.userDetailsService = userDetailsService;
        this.channelRepository = channelRepository;
        this.threadRepository = threadRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.clock = clock;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.CONNECT) {
            authenticateConnect(accessor);
            return message;
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            authorizeSubscribe(accessor);
            return message;
        }

        if (accessor.getCommand() == StompCommand.SEND) {
            return enforceSendRateLimit(message, accessor);
        }

        if (accessor.getCommand() == StompCommand.DISCONNECT) {
            clearSessionState(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
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

        // STOMP 세션에 인증 사용자를 넣어 이후 /user destination과 SUBSCRIBE 인가에서 사용함
        accessor.setUser(authentication);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Long userId = getCurrentUserId(accessor.getUser());
        String destination = accessor.getDestination();
        if (!StringUtils.hasText(destination)) {
            denySubscribe(userId, destination, "WebSocket 구독 경로가 필요합니다.");
        }

        if (PERSONAL_NOTIFICATION_DESTINATION.equals(destination) || PERSONAL_ERROR_DESTINATION.equals(destination) || PERSONAL_WORKSPACE_DESTINATION.equals(destination)) {
            return;
        }

        SubscriptionAuthorizationTarget target = resolveSubscriptionTarget(userId, destination);
        authorizeWorkspaceSubscription(userId, destination, target.workspaceId(), accessor.getSessionId());
    }

    private Message<?> enforceSendRateLimit(Message<?> message, StompHeaderAccessor accessor) {
        Long userId = getCurrentUserId(accessor.getUser());

        if (isTypingSendDestination(accessor.getDestination())) {
            return message;
        }

        String rateLimitKey = rateLimitKey(accessor, userId);

        // 동일 세션의 일반 SEND 폭주가 DB write와 broadcast로 번지는 것을 막음.
        // typing은 고빈도 상태 이벤트라 별도 경로에서 제외하고, 초과 프레임은 연결 종료 없이 drop함.
        SendRateLimitWindow window = sendRateLimitWindows.computeIfAbsent(rateLimitKey, ignored -> new SendRateLimitWindow());

        if (!window.tryConsume(clock.millis())) {
            log.debug(
                    "WebSocket SEND rate limit 초과로 프레임을 drop합니다. userId={}, sessionId={}, destination={}",
                    userId,
                    accessor.getSessionId(),
                    accessor.getDestination()
            );
            return null;
        }

        return message;
    }

    private boolean isTypingSendDestination(String destination) {
        return StringUtils.hasText(destination) && CHANNEL_TYPING_SEND_DESTINATION.matcher(destination).matches();
    }

    private void clearSessionState(StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        clearSessionState(sessionId);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        clearSessionState(event.getSessionId());
    }

    @Scheduled(fixedDelay = CACHE_SWEEP_INTERVAL_MILLIS)
    void sweepExpiredCacheEntries() {
        int removedCount = sweepExpiredCacheEntries(clock.millis());
        if (removedCount > 0) {
            log.debug("Expired WebSocket cache entries swept. removedCount={}", removedCount);
        }
    }

    void clearSessionState(String sessionId) {
        if (StringUtils.hasText(sessionId)) {
            sendRateLimitWindows.remove(sessionId);
            subscribeAuthorizationCache.keySet().removeIf(key -> key.startsWith(sessionCacheKeyPrefix(sessionId)));
        }
    }

    int sweepExpiredCacheEntries(long now) {
        AtomicInteger removedCount = new AtomicInteger();

        // destination -> workspace 캐시는 세션과 무관하므로 주기적으로 만료 엔트리 제거함
        subscriptionWorkspaceCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                removedCount.incrementAndGet();
            }
            return expired;
        });

        subscribeAuthorizationCache.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                removedCount.incrementAndGet();
            }
            return expired;
        });

        sendRateLimitWindows.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().isExpired(now);
            if (expired) {
                removedCount.incrementAndGet();
            }
            return expired;
        });

        return removedCount.get();
    }

    private String rateLimitKey(StompHeaderAccessor accessor, Long userId) {
        String sessionId = accessor.getSessionId();
        if (StringUtils.hasText(sessionId)) {
            return sessionId;
        }
        return "user:" + userId;
    }

    private void denySubscribe(Long userId, String destination, String message) {
        log.warn("WebSocket SUBSCRIBE denied. userId={}, destination={}, reason={}", userId, destination, message);
        throw new AccessDeniedException(message);
    }

    private SubscriptionAuthorizationTarget resolveSubscriptionTarget(Long userId, String destination) {
        long now = clock.millis();
        SubscriptionWorkspaceCacheEntry cached = subscriptionWorkspaceCache.get(destination);
        if (cached != null && !cached.isExpired(now)) {
            return new SubscriptionAuthorizationTarget(cached.workspaceId());
        }

        Optional<Long> workspaceId = resolveWorkspaceId(destination);
        if (workspaceId.isEmpty()) {
            denySubscribe(userId, destination, "허용되지 않은 WebSocket 구독 경로입니다.");
        }

        Long resolvedWorkspaceId = workspaceId.orElseThrow();
        subscriptionWorkspaceCache.put(
                destination,
                new SubscriptionWorkspaceCacheEntry(resolvedWorkspaceId, now + SUBSCRIBE_AUTH_CACHE_TTL_MILLIS)
        );
        return new SubscriptionAuthorizationTarget(resolvedWorkspaceId);
    }

    private void authorizeWorkspaceSubscription(Long userId, String destination, Long workspaceId, String sessionId) {
        String cacheKey = subscribeAuthorizationCacheKey(sessionId, userId, workspaceId);
        long now = clock.millis();
        SubscribeAuthorizationCacheEntry cached = subscribeAuthorizationCache.get(cacheKey);
        if (cached != null && !cached.isExpired(now)) {
            return;
        }

        if (workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId) <= 0) {
            subscribeAuthorizationCache.remove(cacheKey);
            denySubscribe(userId, destination, "WebSocket 구독 권한이 없습니다.");
        }

        subscribeAuthorizationCache.put(cacheKey, new SubscribeAuthorizationCacheEntry(now + SUBSCRIBE_AUTH_CACHE_TTL_MILLIS));
    }

    private String subscribeAuthorizationCacheKey(String sessionId, Long userId, Long workspaceId) {
        if (StringUtils.hasText(sessionId)) {
            return sessionCacheKeyPrefix(sessionId) + "user:" + userId + ":workspace:" + workspaceId;
        }
        return "user:" + userId + ":workspace:" + workspaceId;
    }

    private String sessionCacheKeyPrefix(String sessionId) {
        return SESSION_CACHE_KEY_PREFIX + sessionId + ":";
    }

    private Optional<Long> resolveWorkspaceId(String destination) {
        Matcher workspacePresenceMatcher = WORKSPACE_PRESENCE_DESTINATION.matcher(destination);
        if (workspacePresenceMatcher.matches()) {
            // presence는 destination에 workspaceId가 직접 들어있어 멤버십 검증으로 구독 권한을 판단함.
            return Optional.of(parseId(workspacePresenceMatcher.group(1)));
        }

        Matcher channelEventsMatcher = CHANNEL_EVENTS_DESTINATION.matcher(destination);
        if (channelEventsMatcher.matches()) {
            return channelRepository.findWorkspaceIdById(parseId(channelEventsMatcher.group(1)));
        }

        Matcher legacyChannelEventsMatcher = CHANNEL_LEGACY_EVENTS_DESTINATION.matcher(destination);
        if (legacyChannelEventsMatcher.matches()) {
            return channelRepository.findWorkspaceIdById(parseId(legacyChannelEventsMatcher.group(1)));
        }

        Matcher channelTypingMatcher = CHANNEL_TYPING_DESTINATION.matcher(destination);
        if (channelTypingMatcher.matches()) {
            return channelRepository.findWorkspaceIdById(parseId(channelTypingMatcher.group(1)));
        }

        Matcher threadEventsMatcher = THREAD_EVENTS_DESTINATION.matcher(destination);
        if (threadEventsMatcher.matches()) {
            return threadRepository.findWorkspaceIdById(parseId(threadEventsMatcher.group(1)));
        }

        Matcher workspaceMembersMatcher = WORKSPACE_MEMBERS_DESTINATION.matcher(destination);
        if (workspaceMembersMatcher.matches()) {
            return Optional.of(parseId(workspaceMembersMatcher.group(1)));
        }

        Matcher legacyThreadEventsMatcher = THREAD_LEGACY_EVENTS_DESTINATION.matcher(destination);
        if (legacyThreadEventsMatcher.matches()) {
            return threadRepository.findWorkspaceIdById(parseId(legacyThreadEventsMatcher.group(1)));
        }

        return Optional.empty();
    }

    private Long parseId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("WebSocket 구독 경로가 올바르지 않습니다.", e);
        }
    }

    private Long getCurrentUserId(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        throw new AccessDeniedException("WebSocket 인증 사용자가 필요합니다.");
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = getAuthorizationHeader(accessor);
        if (StringUtils.hasText(authorization)) {
            return extractAuthorizationBearerToken(authorization);
        }

        String accessToken = getTokenHeader(accessor);
        if (StringUtils.hasText(accessToken)) {
            return normalizeRawToken(accessToken);
        }

        Object sessionToken = Optional.ofNullable(accessor.getSessionAttributes())
                .map(attributes -> attributes.get(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE))
                .orElse(null);
        if (sessionToken instanceof String token) {
            return normalizeRawToken(token);
        }

        throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
    }

    private String extractAuthorizationBearerToken(String authorization) {
        if (!authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }

        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }
        return token;
    }

    private String normalizeRawToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }

        if (rawToken.startsWith(BEARER_PREFIX)) {
            return extractAuthorizationBearerToken(rawToken);
        }
        String token = rawToken.trim();
        if (!StringUtils.hasText(token)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }
        return token;
    }

    private String getAuthorizationHeader(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        return accessor.getFirstNativeHeader(LOWERCASE_AUTHORIZATION_HEADER);
    }

    private String getTokenHeader(StompHeaderAccessor accessor) {
        String accessToken = accessor.getFirstNativeHeader(ACCESS_TOKEN_HEADER);
        if (StringUtils.hasText(accessToken)) {
            return accessToken;
        }
        return accessor.getFirstNativeHeader(TOKEN_HEADER);
    }

    private CustomUserDetails loadUserDetails(String token) {
        try {
            Long userId = jwtProvider.getUserId(token);
            return userDetailsService.loadUserById(userId);
        } catch (RuntimeException e) {
            throw new AccessDeniedException("WebSocket 인증 사용자를 찾을 수 없습니다.", e);
        }
    }

    private static final class SendRateLimitWindow {

        private long windowStartedAt;
        private int consumed;

        synchronized boolean tryConsume(long now) {
            if (windowStartedAt == 0L || now - windowStartedAt >= SEND_RATE_LIMIT_WINDOW_MILLIS) {
                windowStartedAt = now;
                consumed = 0;
            }

            if (consumed >= SEND_RATE_LIMIT_CAPACITY) {
                return false;
            }

            consumed++;
            return true;
        }

        synchronized boolean isExpired(long now) {
            return windowStartedAt > 0L && now - windowStartedAt >= SEND_RATE_LIMIT_WINDOW_MILLIS;
        }
    }

    private record SubscriptionAuthorizationTarget(Long workspaceId) {
    }

    private record SubscriptionWorkspaceCacheEntry(Long workspaceId, long expiresAt) {

        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }

    private record SubscribeAuthorizationCacheEntry(long expiresAt) {

        boolean isExpired(long now) {
            return now >= expiresAt;
        }
    }
}
