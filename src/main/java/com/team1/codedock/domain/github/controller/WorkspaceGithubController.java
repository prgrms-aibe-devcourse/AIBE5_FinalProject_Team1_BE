package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepositoryLinkRequest;
import com.team1.codedock.domain.github.dto.GithubRepositoryOverviewResponse;
import com.team1.codedock.domain.github.service.GithubRepositoryService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ApiResponse<List<GithubConnectResponse>> getWorkspaceRepositories(@PathVariable Long workspaceId) {
        return ApiResponse.ok(githubRepositoryService.getWorkspaceRepositories(
                workspaceId, SecurityUtils.getCurrentUserId()));
    }

    @GetMapping("/repositories/{repositoryId}/overview")
    public ApiResponse<GithubRepositoryOverviewResponse> getRepositoryOverview(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId
    ) {
        return ApiResponse.ok(githubRepositoryService.getRepositoryOverview(
                workspaceId, repositoryId, SecurityUtils.getCurrentUserId()));
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
        ChannelListResponse response = githubRepositoryService.createRepositoryChannel(
                workspaceId, SecurityUtils.getCurrentUserId(), request);
        ChatEventResponse<ChannelListResponse> event = ChatEventResponse.of(ChatEventType.CHANNEL_CREATED, response);

        // 새 repository 채널은 아직 채널 토픽을 구독할 수 없으므로 워크스페이스 토픽으로 목록 갱신을 알림
        messagingTemplate.convertAndSend("/topic/workspaces/" + workspaceId + "/channels", event);
        return ApiResponse.ok(response);
    }
}
