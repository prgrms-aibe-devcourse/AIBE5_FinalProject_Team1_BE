package com.team1.codedock.domain.chat.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class ChatMessageEventConsumerTest {

    private final ChatMessageEventConsumer consumer =
            new ChatMessageEventConsumer(new ObjectMapper());

    @Test
    void consumesValidPayloadWithoutError() {
        String payload = """
                {"messageId":1,"channelId":10,"workspaceId":100,
                 "senderMemberId":5,"senderName":"tester","content":"hello",
                 "createdAt":"2026-06-24T00:00:00"}
                """;

        assertThatCode(() -> consumer.consume(payload)).doesNotThrowAnyException();
    }

    @Test
    void swallowsMalformedPayload() {
        // 역직렬화 실패가 컨슈머를 죽이지 않고 흡수되어야 한다.
        assertThatCode(() -> consumer.consume("not-a-json")).doesNotThrowAnyException();
    }
}
