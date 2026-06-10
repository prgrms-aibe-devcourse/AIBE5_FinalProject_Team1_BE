package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.ErdDocumentResponse;
import com.team1.codedock.domain.document.dto.ErdTableResponse;
import com.team1.codedock.domain.document.service.ErdService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/erd")
public class ErdController {

    private final ErdService erdService;

    @PostMapping("/generate")
    public ApiResponse<ErdDocumentResponse> generateErd(@PathVariable Long workspaceId) {
        return ApiResponse.ok(erdService.generateErd(workspaceId));
    }

    @GetMapping
    public ApiResponse<ErdDocumentResponse> getErd(@PathVariable Long workspaceId) {
        return ApiResponse.ok(erdService.getErd(workspaceId));
    }

    @GetMapping("/tables")
    public ApiResponse<List<ErdTableResponse>> getErdTables(@PathVariable Long workspaceId) {
        return ApiResponse.ok(erdService.getErdTables(workspaceId));
    }
}
