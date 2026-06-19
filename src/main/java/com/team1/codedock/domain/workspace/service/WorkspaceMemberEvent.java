package com.team1.codedock.domain.workspace.service;

import java.util.Map;

public record WorkspaceMemberEvent(
        Long workspaceId,
        Map<String, Object> payload
) {
}