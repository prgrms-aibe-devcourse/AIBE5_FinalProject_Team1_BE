package com.team1.codedock.domain.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageEventProducerTest {

    @Mock
    private ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatMessageEvent sampleEvent() {
        return new ChatMessageEvent(1L, 10L, 100L, 5L, "tester", "hello", "2026-06-24T00:00:00");
    }

    @Test
    void publishesToTopicWhenKafkaTemplateAvailable() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        ChatMessageEventProducer producer = new ChatMessageEventProducer(kafkaTemplateProvider, objectMapper);

        producer.publish(sampleEvent());

        verify(kafkaTemplate).send(eq(ChatMessageEventProducer.TOPIC), eq("10"), anyString());
    }

    @Test
    void doesNothingWhenKafkaTemplateMissing() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);
        ChatMessageEventProducer producer = new ChatMessageEventProducer(kafkaTemplateProvider, objectMapper);

        assertThatCode(() -> producer.publish(sampleEvent())).doesNotThrowAnyException();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void swallowsExceptionFromKafkaSend() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker down"));
        ChatMessageEventProducer producer = new ChatMessageEventProducer(kafkaTemplateProvider, objectMapper);

        // 발행 실패가 채팅 전송을 막지 않도록 예외를 흡수해야 한다.
        assertThatCode(() -> producer.publish(sampleEvent())).doesNotThrowAnyException();
    }
}
