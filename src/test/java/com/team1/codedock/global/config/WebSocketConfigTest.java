package com.team1.codedock.global.config;

import com.team1.codedock.global.security.WebSocketAuthChannelInterceptor;
import com.team1.codedock.global.security.WebSocketHandshakeAuthInterceptor;
import com.team1.codedock.global.security.WebSocketStompErrorHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.SockJsServiceRegistration;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketConfigTest {

    private final WebSocketAuthChannelInterceptor interceptor = mock(WebSocketAuthChannelInterceptor.class);
    private final WebSocketHandshakeAuthInterceptor handshakeAuthInterceptor = mock(WebSocketHandshakeAuthInterceptor.class);
    private final WebSocketStompErrorHandler stompErrorHandler = mock(WebSocketStompErrorHandler.class);
    private final WebSocketConfig webSocketConfig = new WebSocketConfig(
            interceptor,
            handshakeAuthInterceptor,
            stompErrorHandler
    );

    @Test
    @DisplayName("WebSocket 캐시 sweep을 위해 scheduling을 활성화한다")
    void websocketConfigEnablesScheduling() {
        assertThat(WebSocketConfig.class).hasAnnotation(EnableScheduling.class);
    }

    @Test
    @DisplayName("configured origin이 좁아도 localhost와 LAN 개발 origin wildcard를 함께 허용한다")
    void resolveAllowedOriginPatternsIncludesLocalDevelopmentWildcards() {
        ReflectionTestUtils.setField(
                webSocketConfig,
                "allowedOriginPatterns",
                new String[]{"http://localhost:5173"}
        );
        ReflectionTestUtils.setField(webSocketConfig, "localNetworkOriginEnabled", true);

        assertThat(webSocketConfig.resolveAllowedOriginPatterns())
                .contains(
                        "http://localhost:5173",
                        "http://10.*:*",
                        "http://172.16.*:*",
                        "http://172.31.*:*",
                        "http://192.168.*:*",
                        "http://*.local:*",
                        "https://10.*:*",
                        "https://172.16.*:*",
                        "https://172.31.*:*",
                        "https://192.168.*:*",
                        "https://*.local:*"
                )
                .doesNotContain("http://172.*:*", "https://172.*:*")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("WebSocket LAN origin pattern은 로컬 네트워크 허용 옵션이 꺼져 있으면 자동 추가하지 않는다")
    void resolveAllowedOriginPatternsDoesNotAutoIncludeLanWildcardsByDefault() {
        ReflectionTestUtils.setField(
                webSocketConfig,
                "allowedOriginPatterns",
                new String[]{"http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*"}
        );

        assertThat(webSocketConfig.resolveAllowedOriginPatterns())
                .containsExactly(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://[::1]:*"
                );
    }

    @Test
    @DisplayName("configured origin pattern의 빈 값과 중복을 정리한다")
    void resolveAllowedOriginPatternsCleansConfiguredPatterns() {
        ReflectionTestUtils.setField(
                webSocketConfig,
                "allowedOriginPatterns",
                new String[]{
                        "http://localhost:*",
                        "",
                        "http://192.168.*:*",
                        "http://localhost:*"
                }
        );
        ReflectionTestUtils.setField(webSocketConfig, "localNetworkOriginEnabled", true);

        assertThat(webSocketConfig.resolveAllowedOriginPatterns())
                .contains(
                        "http://localhost:*",
                        "http://192.168.*:*",
                        "http://10.*:*",
                        "http://172.16.*:*",
                        "http://172.31.*:*",
                        "http://*.local:*",
                        "https://10.*:*",
                        "https://172.16.*:*",
                        "https://172.31.*:*",
                        "https://192.168.*:*",
                        "https://*.local:*"
                )
                .doesNotContain("http://172.*:*", "https://172.*:*")
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("STOMP endpoint는 native WebSocket과 SockJS fallback 모두 같은 origin/interceptor로 등록한다")
    void registerStompEndpointsRegistersNativeAndSockJsEndpoints() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration registration = mock(StompWebSocketEndpointRegistration.class);
        SockJsServiceRegistration sockJsRegistration = mock(SockJsServiceRegistration.class);
        String[] expectedOriginPatterns = {
                "http://localhost:5173",
                "http://10.*:*",
                "http://172.16.*:*",
                "http://172.17.*:*",
                "http://172.18.*:*",
                "http://172.19.*:*",
                "http://172.20.*:*",
                "http://172.21.*:*",
                "http://172.22.*:*",
                "http://172.23.*:*",
                "http://172.24.*:*",
                "http://172.25.*:*",
                "http://172.26.*:*",
                "http://172.27.*:*",
                "http://172.28.*:*",
                "http://172.29.*:*",
                "http://172.30.*:*",
                "http://172.31.*:*",
                "http://192.168.*:*",
                "http://*.local:*",
                "https://10.*:*",
                "https://172.16.*:*",
                "https://172.17.*:*",
                "https://172.18.*:*",
                "https://172.19.*:*",
                "https://172.20.*:*",
                "https://172.21.*:*",
                "https://172.22.*:*",
                "https://172.23.*:*",
                "https://172.24.*:*",
                "https://172.25.*:*",
                "https://172.26.*:*",
                "https://172.27.*:*",
                "https://172.28.*:*",
                "https://172.29.*:*",
                "https://172.30.*:*",
                "https://172.31.*:*",
                "https://192.168.*:*",
                "https://*.local:*"
        };
        ReflectionTestUtils.setField(
                webSocketConfig,
                "allowedOriginPatterns",
                new String[]{"http://localhost:5173"}
        );
        ReflectionTestUtils.setField(webSocketConfig, "localNetworkOriginEnabled", true);

        when(registry.addEndpoint("/ws")).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);
        when(registration.addInterceptors(any(HandshakeInterceptor[].class))).thenReturn(registration);
        when(registration.withSockJS()).thenReturn(sockJsRegistration);

        webSocketConfig.registerStompEndpoints(registry);

        verify(registry).setErrorHandler(stompErrorHandler);
        verify(registry, times(2)).addEndpoint("/ws");
        verify(registration, times(2)).setAllowedOriginPatterns(expectedOriginPatterns);
        verify(registration, times(2)).addInterceptors(handshakeAuthInterceptor);
        verify(registration).withSockJS();
    }

    @Test
    @DisplayName("message broker는 앱 prefix, 구독 prefix, 개인 큐 prefix와 heartbeat scheduler를 설정한다")
    void configureMessageBrokerRegistersPrefixesAndHeartbeatScheduler() {
        TestMessageBrokerRegistry registry = new TestMessageBrokerRegistry();

        webSocketConfig.configureMessageBroker(registry);
        SimpleBrokerMessageHandler simpleBroker = registry.simpleBroker();
        TaskScheduler taskScheduler = simpleBroker.getTaskScheduler();

        try {
            assertThat(registry.applicationDestinationPrefixes()).containsExactly("/app");
            assertThat(simpleBroker.getDestinationPrefixes()).containsExactly("/topic", "/queue");
            assertThat(simpleBroker.getHeartbeatValue()).containsExactly(10_000L, 10_000L);
            assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
            assertThat(((ThreadPoolTaskScheduler) taskScheduler).getThreadNamePrefix()).isEqualTo("ws-heartbeat-");
            assertThat(((ThreadPoolTaskScheduler) taskScheduler).getScheduledThreadPoolExecutor().getCorePoolSize())
                    .isEqualTo(4);
            assertThat(registry.userDestinationPrefix()).isEqualTo("/user");
        } finally {
            if (taskScheduler instanceof ThreadPoolTaskScheduler scheduler) {
                scheduler.shutdown();
            }
        }
    }

    @Test
    @DisplayName("WebSocket heartbeat scheduler는 복수 스레드로 동작한다")
    void heartbeatSchedulerUsesMultipleThreads() {
        ThreadPoolTaskScheduler scheduler = webSocketConfig.webSocketMessageBrokerTaskScheduler();

        try {
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(4);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("ws-heartbeat-");
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    @DisplayName("WebSocket inbound executor 설정을 검증한다")
    void inboundTaskExecutorSettings() {
        ThreadPoolTaskExecutor executor = webSocketConfig.webSocketInboundTaskExecutor();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(8);
            assertThat(executor.getMaxPoolSize()).isEqualTo(16);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("ws-inbound-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(200);
            assertThat(executor.getThreadPoolExecutor().getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("WebSocket outbound executor 설정을 검증한다")
    void outboundTaskExecutorSettings() {
        ThreadPoolTaskExecutor executor = webSocketConfig.webSocketOutboundTaskExecutor();

        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(4);
            assertThat(executor.getMaxPoolSize()).isEqualTo(8);
            assertThat(executor.getThreadNamePrefix()).isEqualTo("ws-outbound-");
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(200);
            assertThat(executor.getThreadPoolExecutor().getKeepAliveTime(TimeUnit.SECONDS)).isEqualTo(60);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("WebSocket transport 제한값을 명시적으로 설정한다")
    void configureWebSocketTransport() {
        TestWebSocketTransportRegistration registration = new TestWebSocketTransportRegistration();

        webSocketConfig.configureWebSocketTransport(registration);

        assertThat(registration.messageSizeLimit()).isEqualTo(64 * 1024);
        assertThat(registration.sendBufferSizeLimit()).isEqualTo(512 * 1024);
        assertThat(registration.sendTimeLimit()).isEqualTo(15_000);
    }

    @Test
    @DisplayName("client inbound channel에 executor와 인증 interceptor를 등록한다")
    void configureClientInboundChannel() {
        TestChannelRegistration registration = new TestChannelRegistration();

        webSocketConfig.configureClientInboundChannel(registration);

        assertThat(registration.hasConfiguredExecutor()).isTrue();
        assertThat(registration.configuredInterceptors()).containsExactly(interceptor);
    }

    @Test
    @DisplayName("client outbound channel에 executor를 등록한다")
    void configureClientOutboundChannel() {
        TestChannelRegistration registration = new TestChannelRegistration();

        webSocketConfig.configureClientOutboundChannel(registration);

        assertThat(registration.hasConfiguredExecutor()).isTrue();
        assertThat(registration.configuredInterceptors()).isEmpty();
    }

    private static class TestChannelRegistration extends ChannelRegistration {

        boolean hasConfiguredExecutor() {
            return hasExecutor();
        }

        List<ChannelInterceptor> configuredInterceptors() {
            return getInterceptors();
        }
    }

    private static class TestWebSocketTransportRegistration extends WebSocketTransportRegistration {

        Integer messageSizeLimit() {
            return getMessageSizeLimit();
        }

        Integer sendBufferSizeLimit() {
            return getSendBufferSizeLimit();
        }

        Integer sendTimeLimit() {
            return getSendTimeLimit();
        }
    }

    private static class TestMessageBrokerRegistry extends MessageBrokerRegistry {

        private final SubscribableChannel brokerChannel;

        TestMessageBrokerRegistry() {
            this(
                    mock(SubscribableChannel.class),
                    mock(MessageChannel.class),
                    mock(SubscribableChannel.class)
            );
        }

        private TestMessageBrokerRegistry(
                SubscribableChannel clientInboundChannel,
                MessageChannel clientOutboundChannel,
                SubscribableChannel brokerChannel
        ) {
            super(clientInboundChannel, clientOutboundChannel);
            this.brokerChannel = brokerChannel;
        }

        Collection<String> applicationDestinationPrefixes() {
            return getApplicationDestinationPrefixes();
        }

        String userDestinationPrefix() {
            return getUserDestinationPrefix();
        }

        SimpleBrokerMessageHandler simpleBroker() {
            return getSimpleBroker(brokerChannel);
        }
    }
}
