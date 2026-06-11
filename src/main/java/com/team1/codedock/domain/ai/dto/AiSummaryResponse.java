package com.team1.codedock.domain.ai.dto;

import com.team1.codedock.domain.ai.service.GeminiClient;

import java.time.LocalDateTime;
import java.util.List;

public record AiSummaryResponse(
        Long id,
        Long pullRequestId,
        String status,
        String riskLevel,
        String summaryText,
        List<String> cautionItems,
        List<String> positiveItems,
        List<GeminiClient.PrFileFeedback> fileFeedbacks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
