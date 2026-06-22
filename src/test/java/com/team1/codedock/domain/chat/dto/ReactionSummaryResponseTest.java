package com.team1.codedock.domain.chat.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.chat.entity.Reaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReactionSummaryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("기존 생성자는 reacted를 false로 내려준다")
    void defaultConstructorUsesNotReacted() {
        ReactionSummaryResponse response = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like",
                3L
        );

        assertThat(response.reacted()).isFalse();
    }

    @Test
    @DisplayName("withReacted는 기존 집계값을 유지하고 reacted만 바꾼 새 응답을 만든다")
    void withReactedKeepsSummaryFields() {
        ReactionSummaryResponse response = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD_REPLY,
                200L,
                "smile",
                2L
        );

        ReactionSummaryResponse reacted = response.withReacted(true);

        assertThat(reacted.targetType()).isEqualTo(Reaction.TARGET_TYPE_THREAD_REPLY);
        assertThat(reacted.targetId()).isEqualTo(200L);
        assertThat(reacted.emoji()).isEqualTo("smile");
        assertThat(reacted.count()).isEqualTo(2L);
        assertThat(reacted.reacted()).isTrue();
        assertThat(response.reacted()).isFalse();
    }

    @Test
    @DisplayName("리액션 집계 JSON은 프론트 계약인 reacted 필드명을 사용한다")
    void serializeReactedFieldName() throws Exception {
        ReactionSummaryResponse response = new ReactionSummaryResponse(
                Reaction.TARGET_TYPE_THREAD,
                100L,
                "like",
                3L,
                true
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.get("reacted").asBoolean()).isTrue();
        assertThat(json.has("userReacted")).isFalse();
    }
}
