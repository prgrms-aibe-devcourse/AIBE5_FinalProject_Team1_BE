package com.team1.codedock.domain.workspace.service;

import java.util.Map;

public record InviteNotificationEvent(
        String userDestinationKey,
        Map<String, Object> payload
) {
}