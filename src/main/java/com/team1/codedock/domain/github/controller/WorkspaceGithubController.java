package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.service.GithubRepositoryService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/github")
public class WorkspaceGithubController {

    private final GithubRepositoryService githubRepositoryService;

    @PostMapping
    public ApiResponse<GithubConnectResponse> connectRepository(
            @PathVariable Long workspaceId,
            @RequestBody @Valid GithubConnectRequest request) {
        return ApiResponse.ok(githubRepositoryService.connectRepository(
                workspaceId, SecurityUtils.getCurrentUserId(), request));
    }
}
