package com.team1.codedock.domain.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEventResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("채팅 WebSocket event type 계약을 유지한다")
    void chatEventTypes() {
        assertThat(ChatEventType.values())
                .containsExactly(
                        ChatEventType.MESSAGE_CREATED,
                        ChatEventType.MESSAGE_UPDATED,
                        ChatEventType.MESSAGE_DELETED,
                        ChatEventType.THREAD_REPLY_CREATED,
                        ChatEventType.THREAD_REPLY_UPDATED,
                        ChatEventType.THREAD_REPLY_DELETED,
                        ChatEventType.REACTION_UPDATED,
                        ChatEventType.CHANNEL_CREATED,
                        ChatEventType.CHANNEL_READ_STATUS_UPDATED,
                        ChatEventType.MENTION_DELETED,
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

    @Test
    @DisplayName("새 실시간 동기화 이벤트는 JSON type 문자열 계약을 유지한다")
    void serializeNewSyncEventTypes() throws Exception {
        assertEventTypeJson(ChatEventType.CHANNEL_CREATED, "CHANNEL_CREATED");
        assertEventTypeJson(ChatEventType.CHANNEL_READ_STATUS_UPDATED, "CHANNEL_READ_STATUS_UPDATED");
        assertEventTypeJson(ChatEventType.MENTION_DELETED, "MENTION_DELETED");
    }

    @Test
    @DisplayName("채팅 WebSocket 응답 JSON은 type과 payload 필드만 노출한다")
    void serializeEnvelopeShape() throws Exception {
        ChatEventResponse<TestPayload> response =
                ChatEventResponse.of(ChatEventType.CHANNEL_CREATED, new TestPayload(10L, "created"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.fieldNames()).toIterable()
                .containsExactly("type", "payload");
        assertThat(json.get("type").asText()).isEqualTo("CHANNEL_CREATED");
        assertThat(json.get("payload").get("id").asLong()).isEqualTo(10L);
        assertThat(json.get("payload").get("content").asText()).isEqualTo("created");
    }

    private void assertEventTypeJson(ChatEventType eventType, String expectedType) throws Exception {
        ChatEventResponse<TestPayload> response = ChatEventResponse.of(eventType, new TestPayload(1L, "payload"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("type").asText()).isEqualTo(expectedType);
    }

    private record TestPayload(Long id, String content) {
    }
}
