package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.DocumentAiGenerateRequest;
import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.service.DocumentAiService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/documents")
public class DocumentAiController {

    private final DocumentAiService documentAiService;

    @PostMapping("/ai-generate")
    public ApiResponse<DocumentResponse> generateDocument(
            @PathVariable Long workspaceId,
            @RequestBody @Valid DocumentAiGenerateRequest request) {
        return ApiResponse.ok(documentAiService.generateDocument(workspaceId, request));
    }
}
