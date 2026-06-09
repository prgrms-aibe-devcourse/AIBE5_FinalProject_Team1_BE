package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.ApiSpecCreateRequest;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.dto.ApiSpecUpdateRequest;
import com.team1.codedock.domain.document.service.ApiSpecService;
import com.team1.codedock.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workspaces/{workspaceId}/api-specs")
public class ApiSpecController {

    private final ApiSpecService apiSpecService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApiSpecResponse> createApiSpec(
            @PathVariable Long workspaceId,
            @Valid @RequestBody ApiSpecCreateRequest request
    ) {
        return ApiResponse.ok(apiSpecService.createApiSpec(workspaceId, request));
    }

    @GetMapping
    public ApiResponse<List<ApiSpecResponse>> getApiSpecs(
            @PathVariable Long workspaceId,
            @RequestParam(required = false) String groupName,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(apiSpecService.getApiSpecs(workspaceId, groupName, status));
    }

    @GetMapping("/{apiSpecId}")
    public ApiResponse<ApiSpecResponse> getApiSpec(
            @PathVariable Long workspaceId,
            @PathVariable Long apiSpecId
    ) {
        return ApiResponse.ok(apiSpecService.getApiSpec(workspaceId, apiSpecId));
    }

    @PatchMapping("/{apiSpecId}")
    public ApiResponse<ApiSpecResponse> updateApiSpec(
            @PathVariable Long workspaceId,
            @PathVariable Long apiSpecId,
            @Valid @RequestBody ApiSpecUpdateRequest request
    ) {
        return ApiResponse.ok(apiSpecService.updateApiSpec(workspaceId, apiSpecId, request));
    }

    @DeleteMapping("/{apiSpecId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiResponse<Void> deleteApiSpec(
            @PathVariable Long workspaceId,
            @PathVariable Long apiSpecId
    ) {
        apiSpecService.deleteApiSpec(workspaceId, apiSpecId);
        return ApiResponse.ok();
    }
}
