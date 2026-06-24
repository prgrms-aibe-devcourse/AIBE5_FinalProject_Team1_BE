package com.team1.codedock.domain.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지 이벤트를 Kafka 토픽으로 발행한다.
 * <p>
 * Kafka가 구성되지 않은 환경(KafkaTemplate 빈 없음, 예: 테스트)에서는 아무 동작도 하지 않으며,
 * 발행 중 예외가 발생해도 채팅 전송 자체를 막지 않도록 내부에서 흡수한다(fire-and-forget).
 */
@Slf4j
@Component
public class ChatMessageEventProducer {

    public static final String TOPIC = "chat-messages";

    private final ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;
    private final ObjectMapper objectMapper;

    public ChatMessageEventProducer(
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider,
            ObjectMapper objectMapper
    ) {
        this.kafkaTemplateProvider = kafkaTemplateProvider;
        this.objectMapper = objectMapper;
    }

    public void publish(ChatMessageEvent event) {
        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate == null) {
            return; // Kafka 미구성 환경: 발행 생략
        }
        try {
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, String.valueOf(event.channelId()), payload);
        } catch (Exception e) {
            log.warn("채팅 메시지 이벤트 발행 실패 (messageId={}): {}", event.messageId(), e.getMessage());
        }
    }
}
