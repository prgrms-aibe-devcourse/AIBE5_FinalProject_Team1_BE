package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.service.GithubRepositoryService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping({
        "/api/workspaces/{workspaceId}/github",
        "/api/v1/workspaces/{workspaceId}/github"
})
public class WorkspaceGithubController {

    private final GithubRepositoryService githubRepositoryService;

    @GetMapping
    public ApiResponse<List<GithubConnectResponse>> getWorkspaceRepositories(@PathVariable Long workspaceId) {
        return ApiResponse.ok(githubRepositoryService.getWorkspaceRepositories(
                workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @PostMapping
    public ApiResponse<GithubConnectResponse> connectRepository(
            @PathVariable Long workspaceId,
            @RequestBody @Valid GithubConnectRequest request) {
        return ApiResponse.ok(githubRepositoryService.connectRepository(
                workspaceId, SecurityUtils.getCurrentUserId(), request));
    }

    @PostMapping("/repositories")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ChannelListResponse> createRepositoryChannel(
            @PathVariable Long workspaceId,
            @RequestBody @Valid GithubRepositoryLinkRequest request
    ) {
        return ApiResponse.ok(githubRepositoryService.createRepositoryChannel(
                workspaceId, SecurityUtils.getCurrentUserId(), request));
    }
}
