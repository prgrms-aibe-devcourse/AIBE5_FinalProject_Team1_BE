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
import static org.mockito.Mockito.verifyNoInteractions;
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

    private ChatMessageEventProducer producer(boolean enabled) {
        return new ChatMessageEventProducer(kafkaTemplateProvider, objectMapper, enabled);
    }

    @Test
    void publishesToTopicWhenEnabledAndKafkaTemplateAvailable() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);

        producer(true).publish(sampleEvent());

        verify(kafkaTemplate).send(eq(ChatMessageEventProducer.TOPIC), eq("10"), anyString());
    }

    @Test
    void doesNothingWhenDisabled() {
        // 플래그가 꺼져 있으면 KafkaTemplate 조회조차 하지 않는다(명시적 no-op).
        assertThatCode(() -> producer(false).publish(sampleEvent())).doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplateProvider, kafkaTemplate);
    }

    @Test
    void doesNothingWhenKafkaTemplateMissing() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(null);

        assertThatCode(() -> producer(true).publish(sampleEvent())).doesNotThrowAnyException();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void swallowsExceptionFromKafkaSend() {
        when(kafkaTemplateProvider.getIfAvailable()).thenReturn(kafkaTemplate);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("broker down"));

        // 발행 실패가 채팅 전송을 막지 않도록 예외를 흡수해야 한다.
        assertThatCode(() -> producer(true).publish(sampleEvent())).doesNotThrowAnyException();
    }
}
