package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.service.ApiSpecAiService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/api-specs")
public class ApiSpecAiController {

    private final ApiSpecAiService apiSpecAiService;

    @PostMapping("/ai-checklist")
    public ApiResponse<List<ApiSpecResponse>> generateChecklist(@PathVariable Long workspaceId) {
        return ApiResponse.ok(apiSpecAiService.generateChecklist(workspaceId));
    }
}
