package com.team1.codedock.domain.github.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.github.dto.GithubIssueWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubPullRequestReviewWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubPullRequestWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubWebhookRegisterResponse;
import com.team1.codedock.domain.github.service.GithubWebhookRegistrationService;
import com.team1.codedock.domain.github.service.GithubWebhookService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.response.ApiResponse;
import com.team1.codedock.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GithubWebhookController {

    private static final String EVENT_ISSUES = "issues";
    private static final String EVENT_PULL_REQUEST = "pull_request";
    private static final String EVENT_PULL_REQUEST_REVIEW = "pull_request_review";

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
        dispatchWebhook(repositoryId, event, signature, rawBody);
    }

    /**
     * GitHub repo id 기반 Webhook 수신 엔드포인트 — DB auto-increment id 대신 불변인 GitHub repo id를
     * 경로에 사용하므로, DB 재생성/레포 재연결 후에도 웹훅 URL을 바꿀 필요가 없다.
     * 같은 GitHub 레포가 여러 워크스페이스에 연결됐을 수 있어 매칭되는 모든 레포를 처리한다.
     */
    @PostMapping("/api/v1/github/webhooks/gh/{githubRepoId}")
    @ResponseStatus(HttpStatus.OK)
    public void receiveWebhookByGithubRepoId(
            @PathVariable String githubRepoId,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestBody byte[] rawBody
    ) {
        List<Long> repositoryIds = githubWebhookService.findRepositoryIdsByGithubRepoId(githubRepoId);
        if (repositoryIds.isEmpty()) {
            throw new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND);
        }

        // 같은 GitHub repo가 여러 워크스페이스에 연결되면 repo row마다 webhook secret이 다를 수 있다.
        // GitHub는 하나의 secret으로만 서명하므로, repo row 중 하나라도 서명을 통과하면 페이로드
        // 진위가 보장된 것으로 보고 전체를 처리한다. 하나도 통과하지 못하면 위조로 간주해 거절한다.
        boolean signatureVerified = repositoryIds.stream()
                .anyMatch(repositoryId -> githubWebhookService.isSignatureValid(repositoryId, signature, rawBody));
        if (!signatureVerified) {
            throw new BusinessException(ErrorCode.GITHUB_WEBHOOK_INVALID);
        }

        int processedCount = 0;
        for (Long repositoryId : repositoryIds) {
            if (processWebhookEvent(repositoryId, event, rawBody)) {
                processedCount++;
            }
        }
        // 매칭된 repo가 있는데 단 하나도 처리되지 못하면 200으로 숨기지 않고 실패를 노출한다(GitHub 재시도 유도).
        if (processedCount == 0) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void dispatchWebhook(Long repositoryId, String event, String signature, byte[] rawBody) {
        githubWebhookService.verifySignature(repositoryId, signature, rawBody);
        processWebhookEvent(repositoryId, event, rawBody);
    }

    // 이벤트 본문을 파싱해 처리한다. 서명 검증은 호출 측 책임이다. 처리 성공 여부를 반환한다
    // (처리 대상이 아닌 이벤트(ping 등)는 성공으로 간주).
    private boolean processWebhookEvent(Long repositoryId, String event, byte[] rawBody) {
        try {
            if (EVENT_ISSUES.equals(event)) {
                GithubIssueWebhookPayload payload = objectMapper.readValue(rawBody, GithubIssueWebhookPayload.class);
                githubWebhookService.processIssueEvent(repositoryId, payload);
            } else if (EVENT_PULL_REQUEST.equals(event)) {
                GithubPullRequestWebhookPayload payload = objectMapper.readValue(rawBody, GithubPullRequestWebhookPayload.class);
                githubWebhookService.processPullRequestEvent(repositoryId, payload);
            } else if (EVENT_PULL_REQUEST_REVIEW.equals(event)) {
                GithubPullRequestReviewWebhookPayload payload = objectMapper.readValue(rawBody, GithubPullRequestReviewWebhookPayload.class);
                githubWebhookService.processPullRequestReviewEvent(repositoryId, payload);
            }
            return true;
        } catch (Exception e) {
            log.warn("Webhook 이벤트 처리 실패 → repoId={}, event={}", repositoryId, event, e);
            return false;
        }
    }

    /**
     * 레포지토리의 기존 PR 목록을 메신저 메시지 포맷으로 반환
     */
    @GetMapping("/api/v1/github/repositories/{repositoryId}/pull-requests")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> getPullRequests(
            @PathVariable Long repositoryId
    ) {
        return ApiResponse.ok(githubWebhookService.getPullRequestsAsMessages(repositoryId));
    }

    /**
     * 특정 PR body를 GitHub API에서 실시간으로 가져옴
     */
    @GetMapping("/api/v1/github/repositories/{repositoryId}/pull-requests/{prNumber}/body")
    public ApiResponse<java.util.Map<String, Object>> getPrBody(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber
    ) {
        return ApiResponse.ok(githubWebhookService.fetchPrBodyFromGithub(repositoryId, prNumber, SecurityUtils.getCurrentUserId()));
    }

    /**
     * 특정 PR의 변경 파일 목록(파일명/status/추가·삭제 수/patch)을 GitHub API에서 가져옴
     */
    @GetMapping("/api/v1/github/repositories/{repositoryId}/pull-requests/{prNumber}/files")
    public ApiResponse<java.util.List<java.util.Map<String, Object>>> getPrFiles(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber
    ) {
        return ApiResponse.ok(githubWebhookService.fetchPrFilesFromGithub(repositoryId, prNumber, SecurityUtils.getCurrentUserId()));
    }

    /**
     * PR을 GitHub에서 실제로 merge
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/pull-requests/{prNumber}/merge")
    public ApiResponse<Void> mergePullRequest(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber
    ) {
        githubWebhookService.mergePullRequestOnGithub(repositoryId, prNumber, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(null);
    }

    /**
     * 현재 유저가 PR을 승인
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/pull-requests/{prNumber}/approve")
    public ApiResponse<Void> approvePullRequest(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber
    ) {
        githubWebhookService.approvePullRequest(repositoryId, prNumber, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(null);
    }

    /**
     * 현재 유저의 PR 리뷰 상태 조회
     */
    @GetMapping("/api/v1/github/repositories/{repositoryId}/pull-requests/{prNumber}/my-review")
    public ApiResponse<java.util.Map<String, Object>> getMyReview(
            @PathVariable Long repositoryId,
            @PathVariable int prNumber
    ) {
        return ApiResponse.ok(githubWebhookService.getMyReview(repositoryId, prNumber, SecurityUtils.getCurrentUserId()));
    }

    /**
     * GitHub API로 기존 PR 목록을 가져와 DB에 동기화
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/sync-pull-requests")
    public ApiResponse<Void> syncPullRequests(@PathVariable Long repositoryId) {
        githubWebhookService.syncPullRequestsFromGithub(repositoryId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(null);
    }

    /**
     * DB 기반 PR 상태를 채팅 메시지 meta와 동기화 (GitHub API 호출 없음)
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/sync-pull-request-statuses")
    public ApiResponse<Void> syncPullRequestStatuses(@PathVariable Long repositoryId) {
        githubWebhookService.syncAllPrStatuses(repositoryId);
        return ApiResponse.ok(null);
    }

    /**
     * GitHub API로 기존 이슈 목록을 가져와 DB/스레드에 동기화 (과거 이슈도 모든 멤버에게 보이도록)
     */
    @PostMapping("/api/v1/github/repositories/{repositoryId}/sync-issues")
    public ApiResponse<Void> syncIssues(@PathVariable Long repositoryId) {
        githubWebhookService.syncIssuesFromGithub(repositoryId, SecurityUtils.getCurrentUserId());
        return ApiResponse.ok(null);
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
