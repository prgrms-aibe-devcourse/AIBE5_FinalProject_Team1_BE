package com.team1.codedock.domain.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지 이벤트를 Kafka 토픽으로 발행한다.
 * <p>
 * 발행 여부는 {@code app.kafka.chat-event.enabled} 플래그로 명시적으로 제어한다.
 * bootstrap-servers 기본값 때문에 운영 환경에서는 KafkaTemplate 빈이 항상 생성될 수 있으므로,
 * 빈 존재 여부에 기대지 않고 플래그가 꺼져 있으면 확실히 no-op 한다.
 * <p>
 * 플래그가 켜져 있어도 발행 중 예외는 내부에서 흡수하여 채팅 전송 자체를 막지 않으며(fire-and-forget),
 * 브로커 무응답 시 호출 스레드가 오래 막히지 않도록 producer {@code max.block.ms}를 짧게 설정한다.
 */
@Slf4j
@Component
public class ChatMessageEventProducer {

    public static final String TOPIC = "chat-messages";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public ChatMessageEventProducer(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper,
            @Value("${app.kafka.chat-event.enabled:false}") boolean enabled
    ) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publish(ChatMessageEvent event) {
        if (!enabled) {
            return; // 기능 비활성화: 명시적 no-op
        }
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return; // Kafka 미구성 환경(예: 테스트): 발행 생략
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.channelId()), payload);
        } catch (Exception e) {
            log.warn("채팅 메시지 이벤트 발행 실패 (messageId={}): {}", event.messageId(), e.getMessage());
        }
    }
}
