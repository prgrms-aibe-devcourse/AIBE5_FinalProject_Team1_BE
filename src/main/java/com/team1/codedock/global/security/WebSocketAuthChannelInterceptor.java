package com.team1.codedock.global.security;

import com.team1.codedock.domain.channel.repository.ChannelRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String LOWERCASE_AUTHORIZATION_HEADER = "authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String PERSONAL_NOTIFICATION_DESTINATION = "/user/queue/notifications";
    private static final String PERSONAL_ERROR_DESTINATION = "/user/queue/errors";
    private static final Pattern CHANNEL_EVENTS_DESTINATION =
            Pattern.compile("^/topic/channels/(\\d+)/events$");
    private static final Pattern CHANNEL_TYPING_DESTINATION =
            Pattern.compile("^/topic/channels/(\\d+)/typing$");
    private static final Pattern THREAD_EVENTS_DESTINATION =
            Pattern.compile("^/topic/threads/(\\d+)/events$");

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ChannelRepository channelRepository;
    private final ThreadRepository threadRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

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
            throw new AccessDeniedException("WebSocket 구독 경로가 필요합니다.");
        }

        if (PERSONAL_NOTIFICATION_DESTINATION.equals(destination) || PERSONAL_ERROR_DESTINATION.equals(destination)) {
            return;
        }

        Optional<Long> workspaceId = resolveWorkspaceId(destination);
        if (workspaceId.isEmpty()) {
            throw new AccessDeniedException("허용되지 않은 WebSocket 구독 경로입니다.");
        }

        if (workspaceMemberRepository.countByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId.get(), userId) <= 0) {
            throw new AccessDeniedException("WebSocket 구독 권한이 없습니다.");
        }
    }

    private Optional<Long> resolveWorkspaceId(String destination) {
        Matcher channelEventsMatcher = CHANNEL_EVENTS_DESTINATION.matcher(destination);
        if (channelEventsMatcher.matches()) {
            return channelRepository.findWorkspaceIdById(parseId(channelEventsMatcher.group(1)));
        }

        Matcher channelTypingMatcher = CHANNEL_TYPING_DESTINATION.matcher(destination);
        if (channelTypingMatcher.matches()) {
            return channelRepository.findWorkspaceIdById(parseId(channelTypingMatcher.group(1)));
        }

        Matcher threadEventsMatcher = THREAD_EVENTS_DESTINATION.matcher(destination);
        if (threadEventsMatcher.matches()) {
            return threadRepository.findWorkspaceIdById(parseId(threadEventsMatcher.group(1)));
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
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket 인증 토큰이 필요합니다.");
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private String getAuthorizationHeader(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authorization)) {
            return authorization;
        }
        return accessor.getFirstNativeHeader(LOWERCASE_AUTHORIZATION_HEADER);
    }

    private CustomUserDetails loadUserDetails(String token) {
        try {
            Long userId = jwtProvider.getUserId(token);
            return userDetailsService.loadUserById(userId);
        } catch (RuntimeException e) {
            throw new AccessDeniedException("WebSocket 인증 사용자를 찾을 수 없습니다.", e);
        }
    }
}
