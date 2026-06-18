package com.team1.codedock.global.config;

import com.team1.codedock.global.security.WebSocketAuthChannelInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketConfigTest {

    private final WebSocketAuthChannelInterceptor interceptor = mock(WebSocketAuthChannelInterceptor.class);
    private final WebSocketConfig webSocketConfig = new WebSocketConfig(interceptor);

    @Test
    @DisplayName("WebSocket 캐시 sweep을 위해 scheduling을 활성화한다")
    void websocketConfigEnablesScheduling() {
        assertThat(WebSocketConfig.class).hasAnnotation(EnableScheduling.class);
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
}
