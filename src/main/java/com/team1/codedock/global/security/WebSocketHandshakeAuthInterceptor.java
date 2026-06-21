package com.team1.codedock.global.security;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class WebSocketHandshakeAuthInterceptor implements HandshakeInterceptor {

    public static final String ACCESS_TOKEN_ATTRIBUTE = "websocketAccessToken";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_QUERY_PARAM = "access_token";
    private static final String TOKEN_QUERY_PARAM = "token";

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        String token = resolveToken(request);
        if (StringUtils.hasText(token)) {
            attributes.put(ACCESS_TOKEN_ATTRIBUTE, token);
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // No cleanup is required. The token is scoped to the WebSocket session attributes.
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(authorization)) {
            return normalizeToken(authorization);
        }

        String accessToken = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(ACCESS_TOKEN_QUERY_PARAM);
        if (StringUtils.hasText(accessToken)) {
            return normalizeToken(accessToken);
        }

        String token = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst(TOKEN_QUERY_PARAM);
        return normalizeToken(token);
    }

    private String normalizeToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        String token = UriUtils.decode(rawToken, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(token)) {
            return null;
        }
        if (token.startsWith(BEARER_PREFIX)) {
            return token.substring(BEARER_PREFIX.length()).trim();
        }
        return token.trim();
    }
}
