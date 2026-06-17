package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;

public record MentionNotificationEvent(
        String userDestinationKey,
        ChatNotificationResponse notification
) {
}
