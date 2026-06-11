package com.team1.codedock.domain.document.dto;

public record SwaggerSyncResponse(String swaggerUrl, int syncedCount, int completedChecklistCount) {}
