package com.team1.codedock.domain.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.github.dto.GithubIssueWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubWebhookRegisterResponse;
import com.team1.codedock.domain.github.service.GithubWebhookRegistrationService;
import com.team1.codedock.domain.github.service.GithubWebhookService;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GithubWebhookController {

    private static final String EVENT_ISSUES = "issues";

    private final GithubWebhookService githubWebhookService;
    private final GithubWebhookRegistrationService githubWebhookRegistrationService;
    private final ObjectMapper objectMapper;

    /**
     * GitHub Webhook 수신 엔드포인트 — 인증 불필요 (HMAC 서명 검증으로 대체)
     */
    @PostMapping("/api/v1/github/webhooks/{repositoryId}")
    @ResponseStatus(HttpStatus.OK)
    public void receiveWebhook(
            @PathVariable Long repositoryId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody
    ) {
        githubWebhookService.verifySignature(repositoryId, signature, rawBody);

        if (EVENT_ISSUES.equals(event)) {
            try {
                GithubIssueWebhookPayload payload = objectMapper.readValue(rawBody, GithubIssueWebhookPayload.class);
                githubWebhookService.processIssueEvent(repositoryId, payload);
            } catch (Exception e) {
                log.warn("이슈 Webhook 처리 실패 → repoId={}", repositoryId, e);
            }
        }
    }

    /**
     * 레포지토리의 기존 이슈 상태를 채팅 메시지 meta와 동기화
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/sync-issue-statuses")
    public ApiResponse<Void> syncIssueStatuses(@PathVariable Long repositoryId) {
        githubWebhookService.syncAllIssueStatuses(repositoryId);
        return ApiResponse.ok(null);
    }

    /**
     * 워크스페이스 레포지토리에 GitHub Webhook 등록
     */
    @PostMapping({
            "/api/workspaces/{workspaceId}/github/repositories/{repositoryId}/webhook",
            "/api/v1/workspaces/{workspaceId}/github/repositories/{repositoryId}/webhook"
    })
    public ApiResponse<GithubWebhookRegisterResponse> registerWebhook(
            @PathVariable Long workspaceId,
            @PathVariable Long repositoryId
    ) {
        return ApiResponse.ok(githubWebhookRegistrationService.registerWebhook(
                workspaceId, repositoryId, SecurityUtils.getCurrentUserId()));
    }
}
