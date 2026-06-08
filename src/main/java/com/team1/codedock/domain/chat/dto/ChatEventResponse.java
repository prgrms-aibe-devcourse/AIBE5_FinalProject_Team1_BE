package com.team1.codedock.domain.chat.dto;

public record ChatEventResponse<T>(
        ChatEventType type,
        T payload
) {
    public static <T> ChatEventResponse<T> of(ChatEventType type, T payload) {
        return new ChatEventResponse<>(type, payload);
    }
}
