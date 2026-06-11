package com.team1.codedock.domain.ai.controller;

import com.team1.codedock.domain.ai.dto.AiSummaryResponse;
import com.team1.codedock.domain.ai.service.AiSummaryService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/pull-requests/{prId}/ai-summary")
public class AiSummaryController {

    private final AiSummaryService aiSummaryService;

    @PostMapping
    public ApiResponse<AiSummaryResponse> generateSummary(
            @PathVariable Long workspaceId,
            @PathVariable Long prId) {
        return ApiResponse.ok(aiSummaryService.generateSummary(workspaceId, prId));
    }

    @GetMapping
    public ApiResponse<AiSummaryResponse> getSummary(
            @PathVariable Long workspaceId,
            @PathVariable Long prId) {
        return ApiResponse.ok(aiSummaryService.getSummary(workspaceId, prId));
    }
}
