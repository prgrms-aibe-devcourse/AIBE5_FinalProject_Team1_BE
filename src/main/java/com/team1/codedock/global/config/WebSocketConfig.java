package com.team1.codedock.global.config;

import com.team1.codedock.global.security.WebSocketAuthChannelInterceptor;
import com.team1.codedock.global.security.WebSocketHandshakeAuthInterceptor;
import com.team1.codedock.global.security.WebSocketStompErrorHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String[] LOCAL_NETWORK_ORIGIN_PATTERNS = {
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
    private static final long HEARTBEAT_INTERVAL_MS = 10_000L;
    private static final int MESSAGE_SIZE_LIMIT_BYTES = 64 * 1024;
    private static final int SEND_BUFFER_SIZE_LIMIT_BYTES = 512 * 1024;
    private static final int SEND_TIME_LIMIT_MS = 15_000;
    private static final int HEARTBEAT_SCHEDULER_POOL_SIZE = 4;
    private static final int INBOUND_CORE_POOL_SIZE = 8;
    private static final int INBOUND_MAX_POOL_SIZE = 16;
    private static final int OUTBOUND_CORE_POOL_SIZE = 4;
    private static final int OUTBOUND_MAX_POOL_SIZE = 8;
    private static final int WEBSOCKET_QUEUE_CAPACITY = 200;
    private static final int WEBSOCKET_THREAD_KEEP_ALIVE_SECONDS = 60;
    private static final int WEBSOCKET_AWAIT_TERMINATION_SECONDS = 5;

    private final WebSocketAuthChannelInterceptor webSocketAuthChannelInterceptor;
    private final WebSocketHandshakeAuthInterceptor webSocketHandshakeAuthInterceptor;
    private final WebSocketStompErrorHandler webSocketStompErrorHandler;

    @Value("${app.websocket.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://[::1]:*}")
    private String[] allowedOriginPatterns;

    @Value("${app.local-network-origin-enabled:false}")
    private boolean localNetworkOriginEnabled;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(webSocketStompErrorHandler);

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(resolveAllowedOriginPatterns())
                .addInterceptors(webSocketHandshakeAuthInterceptor);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(resolveAllowedOriginPatterns())
                .addInterceptors(webSocketHandshakeAuthInterceptor)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(webSocketInboundTaskExecutor());
        registration.interceptors(webSocketAuthChannelInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor(webSocketOutboundTaskExecutor());
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(MESSAGE_SIZE_LIMIT_BYTES);
        registry.setSendBufferSizeLimit(SEND_BUFFER_SIZE_LIMIT_BYTES);
        registry.setSendTimeLimit(SEND_TIME_LIMIT_MS);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS})
                .setTaskScheduler(webSocketMessageBrokerTaskScheduler());
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public ThreadPoolTaskScheduler webSocketMessageBrokerTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(HEARTBEAT_SCHEDULER_POOL_SIZE);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setAwaitTerminationSeconds(WEBSOCKET_AWAIT_TERMINATION_SECONDS);
        scheduler.initialize();
        return scheduler;
    }

    @Bean
    public ThreadPoolTaskExecutor webSocketInboundTaskExecutor() {
        return webSocketTaskExecutor(
                "ws-inbound-",
                INBOUND_CORE_POOL_SIZE,
                INBOUND_MAX_POOL_SIZE
        );
    }

    @Bean
    public ThreadPoolTaskExecutor webSocketOutboundTaskExecutor() {
        return webSocketTaskExecutor(
                "ws-outbound-",
                OUTBOUND_CORE_POOL_SIZE,
                OUTBOUND_MAX_POOL_SIZE
        );
    }

    private ThreadPoolTaskExecutor webSocketTaskExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(WEBSOCKET_QUEUE_CAPACITY);
        executor.setKeepAliveSeconds(WEBSOCKET_THREAD_KEEP_ALIVE_SECONDS);
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.setAwaitTerminationSeconds(WEBSOCKET_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        return executor;
    }

    String[] resolveAllowedOriginPatterns() {
        String[] localNetworkPatterns = localNetworkOriginEnabled
                ? LOCAL_NETWORK_ORIGIN_PATTERNS
                : new String[0];

        return Arrays.stream(concat(allowedOriginPatterns, localNetworkPatterns))
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .distinct()
                .toArray(String[]::new);
    }

    private String[] concat(String[] first, String[] second) {
        String[] safeFirst = first == null ? new String[0] : first;
        String[] safeSecond = second == null ? new String[0] : second;
        String[] result = Arrays.copyOf(safeFirst, safeFirst.length + safeSecond.length);
        System.arraycopy(safeSecond, 0, result, safeFirst.length, safeSecond.length);
        return result;
    }
}
