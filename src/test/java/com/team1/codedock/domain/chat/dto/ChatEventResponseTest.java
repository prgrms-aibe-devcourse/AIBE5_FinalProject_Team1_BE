package com.team1.codedock.domain.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEventResponseTest {

    @Test
    @DisplayName("채팅 WebSocket event type 계약을 유지한다")
    void chatEventTypes() {
        assertThat(ChatEventType.values())
                .containsExactly(
                        ChatEventType.MESSAGE_CREATED,
                        ChatEventType.MESSAGE_UPDATED,
                        ChatEventType.MESSAGE_DELETED,
                        ChatEventType.THREAD_REPLY_CREATED,
                        ChatEventType.REACTION_UPDATED,
                        ChatEventType.TYPING,
                        ChatEventType.NOTIFICATION_CREATED
                );
    }

    @Test
    @DisplayName("채팅 WebSocket 응답은 type과 payload envelope로 감싼다")
    void createEnvelope() {
        TestPayload payload = new TestPayload(1L, "hello");

        ChatEventResponse<TestPayload> response =
                ChatEventResponse.of(ChatEventType.MESSAGE_CREATED, payload);

        assertThat(response.type()).isEqualTo(ChatEventType.MESSAGE_CREATED);
        assertThat(response.payload()).isEqualTo(payload);
    }

    private record TestPayload(Long id, String content) {
    }
}
