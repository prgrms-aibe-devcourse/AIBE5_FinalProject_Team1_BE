package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.SwaggerSyncResponse;
import com.team1.codedock.domain.document.dto.SwaggerUrlRequest;
import com.team1.codedock.domain.document.service.ApiSpecSwaggerService;
import com.team1.codedock.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/api-specs")
public class ApiSpecSwaggerController {

    private final ApiSpecSwaggerService apiSpecSwaggerService;

    @PostMapping("/swagger-url")
    public ApiResponse<SwaggerSyncResponse> registerSwaggerUrl(
            @PathVariable Long workspaceId,
            @RequestBody SwaggerUrlRequest request) {
        return ApiResponse.ok(apiSpecSwaggerService.registerAndSync(workspaceId, request.swaggerUrl()));
    }

    @GetMapping("/swagger-url")
    public ApiResponse<String> getSwaggerUrl(@PathVariable Long workspaceId) {
        return ApiResponse.ok(apiSpecSwaggerService.getSwaggerUrl(workspaceId));
    }

    @PostMapping("/swagger-url/resync")
    public ApiResponse<SwaggerSyncResponse> resync(@PathVariable Long workspaceId) {
        return ApiResponse.ok(apiSpecSwaggerService.resync(workspaceId));
    }
}
