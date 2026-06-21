package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSocketHandshakeAuthInterceptorTest {

    private final WebSocketHandshakeAuthInterceptor interceptor = new WebSocketHandshakeAuthInterceptor();

    @Test
    @DisplayName("handshake Authorization 헤더의 Bearer 토큰을 세션 attribute에 저장한다")
    void beforeHandshakeStoresAuthorizationBearerToken() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws", "Bearer access-token");

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "access-token");
    }

    @Test
    @DisplayName("handshake Authorization 헤더는 query token보다 우선한다")
    void beforeHandshakePrefersAuthorizationHeaderOverQueryTokens() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request(
                "http://localhost:8080/ws?access_token=query-token&token=raw-token",
                "Bearer header-token"
        );

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "header-token");
    }

    @Test
    @DisplayName("handshake Authorization 헤더가 Bearer 형식이 아니어도 원문 토큰으로 저장한다")
    void beforeHandshakeStoresRawAuthorizationToken() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws", "raw-header-token");

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "raw-header-token");
    }

    @Test
    @DisplayName("handshake access_token query parameter를 세션 attribute에 저장한다")
    void beforeHandshakeStoresAccessTokenQueryParameter() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws?access_token=query-token", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "query-token");
    }

    @Test
    @DisplayName("handshake access_token query parameter는 token parameter보다 우선한다")
    void beforeHandshakePrefersAccessTokenQueryParameterOverTokenParameter() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws?access_token=access-token&token=raw-token", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "access-token");
    }

    @Test
    @DisplayName("handshake query token의 Bearer prefix, URL encoding, 공백을 정리한다")
    void beforeHandshakeNormalizesBearerQueryToken() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws?access_token=Bearer%20query-token%20%20", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "query-token");
    }

    @Test
    @DisplayName("handshake token query parameter도 세션 attribute에 저장한다")
    void beforeHandshakeStoresTokenQueryParameter() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws?token=raw-token", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes)
                .containsEntry(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE, "raw-token");
    }

    @Test
    @DisplayName("handshake token이 blank이면 세션 attribute를 추가하지 않는다")
    void beforeHandshakeDoesNotStoreBlankToken() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws?access_token=%20&token=", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE);
    }

    @Test
    @DisplayName("handshake token이 없으면 세션 attribute를 추가하지 않고 통과한다")
    void beforeHandshakePassesWithoutToken() {
        Map<String, Object> attributes = new HashMap<>();
        ServerHttpRequest request = request("http://localhost:8080/ws", null);

        boolean result = interceptor.beforeHandshake(
                request,
                mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class),
                attributes
        );

        assertThat(result).isTrue();
        assertThat(attributes).doesNotContainKey(WebSocketHandshakeAuthInterceptor.ACCESS_TOKEN_ATTRIBUTE);
    }

    private ServerHttpRequest request(String uri, String authorization) {
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        when(request.getHeaders()).thenReturn(headers);
        when(request.getURI()).thenReturn(URI.create(uri));
        return request;
    }
}
