package com.team1.codedock.domain.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 채팅 메시지 이벤트를 소비한다.
 * <p>
 * 현재는 이벤트 스트림 파이프라인 검증을 위해 로깅만 수행한다.
 * 이후 검색 인덱싱 / 알림 / 활동 통계 등 후처리로 확장한다.
 * <p>
 * Kafka 미구성 환경(테스트 등)에서는 @KafkaListener 인프라가 등록되지 않아
 * 리스너가 비활성화되며, 빈 자체는 정상 로드된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageEventConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = ChatMessageEventProducer.TOPIC,
            groupId = "${spring.kafka.consumer.group-id:codedock}"
    )
    public void consume(String payload) {
        try {
            ChatMessageEvent event = objectMapper.readValue(payload, ChatMessageEvent.class);
            log.info(
                    "[chat-event] messageId={}, channelId={}, workspaceId={}, sender={}",
                    event.messageId(), event.channelId(), event.workspaceId(), event.senderName()
            );
        } catch (Exception e) {
            log.warn("채팅 메시지 이벤트 소비 실패: {}", e.getMessage());
        }
    }
}
