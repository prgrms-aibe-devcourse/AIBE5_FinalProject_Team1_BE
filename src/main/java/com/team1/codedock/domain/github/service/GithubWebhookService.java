package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.github.dto.GithubIssueWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubPullRequestWebhookPayload;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.ai.service.AiSummaryService;
import com.team1.codedock.domain.github.dto.GithubPullRequestReviewWebhookPayload;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.entity.PullRequestFile;
import com.team1.codedock.domain.pr.entity.PullRequestReview;
import com.team1.codedock.domain.pr.entity.PullRequestReviewRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestFileRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.entity.IssueAssignee;
import com.team1.codedock.domain.issue.entity.IssueLabel;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.issue.repository.IssueLabelRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GithubWebhookService {

    private static final String ACTION_OPENED = "opened";
    private static final String ACTION_CLOSED = "closed";
    private static final String ACTION_REOPENED = "reopened";
    private static final String ACTION_EDITED = "edited";
    private static final String ACTION_LABELED = "labeled";
    private static final String ACTION_UNLABELED = "unlabeled";
    private static final String ACTION_ASSIGNED = "assigned";
    private static final String ACTION_UNASSIGNED = "unassigned";

    private final GithubRepositoryRepository githubRepositoryRepository;
    private final GithubIssueRepository githubIssueRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final GithubPullRequestRepository githubPullRequestRepository;
    private final ThreadRepository threadRepository;
    private final ThreadAttachmentRepository threadAttachmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final GithubApiClient githubApiClient;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final PullRequestReviewRepository pullRequestReviewRepository;
    private final PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    private final PullRequestFileRepository pullRequestFileRepository;
    private final IssueAssigneeRepository issueAssigneeRepository;
    private final AiSummaryService aiSummaryService;
    private final GithubWebhookEventService githubWebhookEventService;

    public void verifySignature(Long repositoryId, String signatureHeader, byte[] rawBody) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        String secret = repo.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            return;
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new BusinessException(ErrorCode.GITHUB_WEBHOOK_INVALID);
        }

        String expected = "sha256=" + computeHmacSha256(secret, rawBody);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.GITHUB_WEBHOOK_INVALID);
        }
    }

    @Transactional
    public void processIssueEvent(Long repositoryId, GithubIssueWebhookPayload payload) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        repo.recordWebhookDelivery("success");

        GithubIssueWebhookPayload.IssueDto dto = payload.issue();
        String githubIssueId = String.valueOf(dto.id());
        String action = payload.action();

        Optional<GithubIssue> existing =
                githubIssueRepository.findByRepository_IdAndGithubIssueId(repositoryId, githubIssueId);

        GithubIssue issue;

        if (existing.isEmpty()) {
            if (!ACTION_OPENED.equals(action)) {
                log.debug("이슈 미등록 상태에서 non-opened 이벤트 수신 → skip, repoId={}, action={}", repositoryId, action);
                return;
            }
            Channel channel = repo.getWorkspace().getId() != null
                    ? findChannelForRepo(repo)
                    : null;
            if (channel == null) {
                log.warn("레포지토리 채널을 찾을 수 없음 → repoId={}", repositoryId);
                return;
            }
            issue = GithubIssue.create(
                    repo, channel, githubIssueId, dto.number(), dto.title(),
                    dto.body(), dto.state(), dto.htmlUrl(),
                    dto.user() != null ? dto.user().login() : null,
                    buildLabelsJson(dto.labels()),
                    toLocalDateTime(dto.closedAt()),
                    toLocalDateTime(dto.createdAt()),
                    toLocalDateTime(dto.updatedAt())
            );
            issue = githubIssueRepository.save(issue);
            saveLabels(issue, dto.labels());
            syncIssueAssignees(issue, dto.assignees(), repo);
            broadcastBotNotification(issue, dto);
            githubWebhookEventService.onIssueCreated(
                    repo.getWorkspace().getId(), issue.getId(),
                    dto.user() != null ? dto.user().login() : null,
                    dto.title(), repo.getId(), repo.getName(), (long) dto.number());
        } else {
            issue = existing.get();
            issue.syncFromWebhook(
                    dto.title(), dto.body(), dto.state(), dto.htmlUrl(),
                    buildLabelsJson(dto.labels()),
                    toLocalDateTime(dto.closedAt()),
                    toLocalDateTime(dto.updatedAt())
            );
            if (ACTION_LABELED.equals(action) || ACTION_UNLABELED.equals(action)) {
                issueLabelRepository.deleteAllByGithubIssue_Id(issue.getId());
                saveLabels(issue, dto.labels());
            }
            if (ACTION_ASSIGNED.equals(action) || ACTION_UNASSIGNED.equals(action)) {
                syncIssueAssignees(issue, dto.assignees(), repo);
            }
            if (ACTION_CLOSED.equals(action) || ACTION_REOPENED.equals(action)
                    || ACTION_LABELED.equals(action) || ACTION_UNLABELED.equals(action)
                    || ACTION_ASSIGNED.equals(action) || ACTION_UNASSIGNED.equals(action)
                    || ACTION_EDITED.equals(action)) {
                broadcastIssueStatusUpdate(issue, dto);
            }
        }
    }

    @Transactional
    public void processPullRequestEvent(Long repositoryId, GithubPullRequestWebhookPayload payload) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        repo.recordWebhookDelivery("success");

        GithubPullRequestWebhookPayload.PullRequestDto dto = payload.pullRequest();
        if (dto == null) return;

        String action = payload.action();
        String githubPrId = String.valueOf(dto.id());

        Optional<GithubPullRequest> existing =
                githubPullRequestRepository.findByRepository_IdAndGithubPrId(repositoryId, githubPrId);

        Channel channel = getRepoChannel(repo);
        if (channel == null) {
            log.warn("레포지토리 채널을 찾을 수 없음 → repoId={}", repositoryId);
            return;
        }

        if (existing.isEmpty()) {
            if (!ACTION_OPENED.equals(action)) return;
            GithubPullRequest pr = GithubPullRequest.create(
                    repo, channel,
                    githubPrId,
                    dto.number(),
                    dto.title(),
                    dto.body(),
                    dto.state(),
                    dto.htmlUrl(),
                    dto.user() != null ? dto.user().login() : null,
                    dto.head() != null ? dto.head().ref() : null,
                    dto.base() != null ? dto.base().ref() : null,
                    null,
                    dto.additions(),
                    dto.deletions(),
                    dto.changedFiles(),
                    Boolean.TRUE.equals(dto.merged()) ? toLocalDateTime(dto.mergedAt()) : null,
                    toLocalDateTime(dto.createdAt()),
                    toLocalDateTime(dto.updatedAt()),
                    "[]"
            );
            GithubPullRequest savedPr = githubPullRequestRepository.save(pr);
            syncReviewRequests(savedPr, dto.requestedReviewers(), repo);
            broadcastPrBotNotification(repo, channel, savedPr, dto);
            githubWebhookEventService.onPrCreated(
                    repo.getWorkspace().getId(), savedPr.getId(),
                    dto.user() != null ? dto.user().login() : null,
                    dto.title(), repo.getId(), repo.getName(), (long) dto.number());

            savePullRequestFiles(repo, savedPr, dto.number());
            aiSummaryService.generateSummaryForWebhook(savedPr.getId());
        } else {
            GithubPullRequest pr = existing.get();
            if (ACTION_CLOSED.equals(action) || "reopened".equals(action)
                    || "synchronize".equals(action) || "edited".equals(action)) {
                // merged: true이면 GithubPullRequest.mergedAt 저장 (syncAllPrStatuses의 진실의 원천)
                if (Boolean.TRUE.equals(dto.merged()) && pr.getMergedAt() == null) {
                    pr.updateMergedAt(dto.mergedAt() != null ? toLocalDateTime(dto.mergedAt()) : java.time.LocalDateTime.now());
                    githubPullRequestRepository.save(pr);
                } else if ("reopened".equals(action)) {
                    pr.updateState("open");
                    githubPullRequestRepository.save(pr);
                }
                broadcastPrStatusUpdate(pr, repo, channel, dto);
            } else if ("review_requested".equals(action) || "review_request_removed".equals(action)) {
                syncReviewRequests(pr, dto.requestedReviewers(), repo);
            }
        }
    }

    private void broadcastPrBotNotification(GithubRepository repo,
                                             Channel channel,
                                             GithubPullRequest savedPr,
                                             GithubPullRequestWebhookPayload.PullRequestDto dto) {
        String content = String.format("PR #%d opened by %s: %s",
                dto.number(),
                dto.user() != null ? dto.user().login() : "unknown",
                dto.title());

        Thread thread = Thread.createBotNotification(
                channel, content, Thread.THREADABLE_TYPE_GITHUB_PR, savedPr.getId());
        Thread savedThread = threadRepository.save(thread);

        String meta = buildPrMeta(repo, dto);
        ThreadAttachment attachment = ThreadAttachment.create(
                savedThread, "pr", savedPr.getId(),
                dto.htmlUrl(), "PR #" + dto.number() + ": " + dto.title(),
                dto.user() != null ? dto.user().login() : null,
                meta, null, null, null
        );
        ThreadAttachment savedAttachment = threadAttachmentRepository.save(attachment);

        ChannelMessageResponse response = ChannelMessageResponse.fromBot(
                savedThread, "GitHub Bot",
                List.of(ThreadAttachmentResponse.from(savedAttachment))
        );

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channel.getId() + "/events",
                ChatEventResponse.of(ChatEventType.MESSAGE_CREATED, response)
        );
    }

    private void broadcastPrStatusUpdate(GithubPullRequest pr,
                                          GithubRepository repo,
                                          Channel channel,
                                          GithubPullRequestWebhookPayload.PullRequestDto dto) {
        threadRepository.findByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_PR, pr.getId())
                .ifPresent(thread -> {
                    long approvedCount = pullRequestReviewRepository
                            .countByGithubPullRequest_IdAndReviewState(pr.getId(), "approved");
                    String resolvedStatus = resolvePrStatus(pr, approvedCount, dto.state());

                    List<ThreadAttachment> attachments = threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(thread.getId());
                    if (!attachments.isEmpty()) {
                        ThreadAttachment att = attachments.get(0);
                        try {
                            // 기존 meta를 보존한 채 변경 가능한 필드만 갱신한다.
                            // (buildPrMeta로 통째 재생성하면 prStatus가 GitHub "open"으로,
                            //  approved 카운트가 0으로 덮어써지는 버그가 있었음)
                            @SuppressWarnings("unchecked")
                            Map<String, Object> meta = att.getMeta() != null
                                    ? objectMapper.readValue(att.getMeta(), Map.class)
                                    : new HashMap<>();
                            meta.put("prStatus", resolvedStatus);
                            meta.put("approved", (int) approvedCount);
                            meta.put("prTitle", dto.title());
                            if (dto.body() != null) meta.put("prBody", dto.body());
                            if (dto.additions() != null) meta.put("additions", dto.additions());
                            if (dto.deletions() != null) meta.put("deletions", dto.deletions());
                            if (dto.changedFiles() != null) meta.put("filesChanged", dto.changedFiles());
                            if (dto.createdAt() != null) meta.put("githubCreatedAt", dto.createdAt().toString());
                            // 병합 시각: webhook merged_at 우선, 없으면 DB mergedAt
                            if (dto.mergedAt() != null) meta.put("githubMergedAt", dto.mergedAt().toString());
                            else if (pr.getMergedAt() != null) meta.put("githubMergedAt", pr.getMergedAt().toString());
                            att.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(att);
                        } catch (Exception e) {
                            log.warn("PR status meta 갱신 실패 → prId={}", pr.getId(), e);
                        }
                    }

                    broadcastPrMessageUpdated(thread, channel);
                });
    }

    /**
     * PR 표시 상태의 단일 진실 원천(single source of truth).
     * DB 엔티티 상태(mergedAt/state) + CodeDock 승인 기록을 우선하고,
     * 그 외에는 GitHub 원본 상태(githubState: open/closed)를 따른다.
     * GitHub의 "open"이 CodeDock의 merged/approved를 덮어쓰지 못하게 한다.
     */
    private String resolvePrStatus(GithubPullRequest pr, long approvedCount, String githubState) {
        if (pr.getMergedAt() != null || "merged".equals(pr.getState())) {
            return "merged";
        }
        if ("closed".equals(githubState)) {
            return "closed";
        }
        if ("approved".equals(pr.getState()) || approvedCount > 0) {
            return "approved";
        }
        return githubState != null ? githubState : pr.getState();
    }

    /** PR 스레드의 현재 첨부 상태를 MESSAGE_UPDATED로 브로드캐스트 (이슈 닫힘과 동일 패턴). */
    private void broadcastPrMessageUpdated(Thread thread, Channel channel) {
        List<ThreadAttachmentResponse> attachmentResponses = threadAttachmentRepository
                .findAllByThread_IdOrderByIdAsc(thread.getId())
                .stream()
                .map(ThreadAttachmentResponse::from)
                .toList();

        ChannelMessageResponse response = ChannelMessageResponse.fromBot(
                thread, "GitHub Bot", attachmentResponses
        );

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channel.getId() + "/events",
                ChatEventResponse.of(ChatEventType.MESSAGE_UPDATED, response)
        );
    }

    private void savePullRequestFiles(GithubRepository repo, GithubPullRequest savedPr, int prNumber) {
        String token = null;
        if (repo.getWorkspace() != null) {
            token = workspaceMemberRepository
                    .findAllByWorkspace_IdAndIsActiveTrue(repo.getWorkspace().getId())
                    .stream()
                    .map(m -> m.getUser().getGithubAccessToken())
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        if (token == null) {
            log.warn("GitHub 토큰 없음 → PullRequestFile 저장 스킵, prId={}", savedPr.getId());
            return;
        }
        try {
            List<GithubApiClient.GithubPrFileItem> files = githubApiClient.fetchPullRequestFiles(
                    repo.getOwner(), repo.getName(), prNumber, token);
            List<PullRequestFile> prFiles = files.stream()
                    .map(f -> PullRequestFile.create(savedPr, f.filename(), f.status(),
                            f.additions() != null ? f.additions() : 0,
                            f.deletions() != null ? f.deletions() : 0,
                            f.filename(), f.patch()))
                    .toList();
            pullRequestFileRepository.saveAll(prFiles);
        } catch (Exception e) {
            log.warn("PullRequestFile 저장 실패 → prId={}", savedPr.getId(), e);
        }
    }

    private String buildPrMeta(GithubRepository repo, GithubPullRequestWebhookPayload.PullRequestDto dto) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("prNumber", dto.number());
        meta.put("prTitle", dto.title());
        meta.put("prBody", dto.body() != null ? dto.body() : "");
        meta.put("prStatus", Boolean.TRUE.equals(dto.merged()) ? "merged" : dto.state());
        meta.put("prAuthor", dto.user() != null ? dto.user().login() : null);
        meta.put("prUrl", dto.htmlUrl());
        meta.put("repository", repo.getName());
        meta.put("branch", dto.head() != null ? dto.head().ref() : null);
        meta.put("additions", dto.additions() != null ? dto.additions() : 0);
        meta.put("deletions", dto.deletions() != null ? dto.deletions() : 0);
        meta.put("filesChanged", dto.changedFiles() != null ? dto.changedFiles() : 0);
        meta.put("approved", 0);
        meta.put("pending", dto.requestedReviewers() != null ? dto.requestedReviewers().size() : 0);
        meta.put("aiRisk", "Medium");
        meta.put("passed", 0);
        meta.put("labels", dto.labels() != null
                ? dto.labels().stream().map(GithubPullRequestWebhookPayload.LabelDto::name).toList()
                : List.of());
        meta.put("githubCreatedAt", dto.createdAt() != null ? dto.createdAt().toString() : null);
        meta.put("githubMergedAt", dto.mergedAt() != null ? dto.mergedAt().toString() : null);
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.warn("PR 메타 JSON 직렬화 실패", e);
            return "{}";
        }
    }

    private String buildCommitsJson(List<GithubApiClient.GithubCommitItem> commits) {
        List<Map<String, String>> list = commits.stream().map(c -> {
            Map<String, String> m = new HashMap<>();
            m.put("sha", c.sha() != null ? c.sha().substring(0, Math.min(7, c.sha().length())) : "");
            m.put("message", c.commit() != null ? c.commit().message() : "");
            m.put("author", c.commit() != null && c.commit().author() != null ? c.commit().author().name() : "");
            m.put("date", c.commit() != null && c.commit().author() != null ? c.commit().author().date() : "");
            return m;
        }).toList();
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    // GitHub에서 해당 PR을 APPROVED로 리뷰한 고유 사용자 수 (사용자별 마지막 결정 상태 기준)
    private long countGithubApprovedReviewers(GithubRepository repo, int prNumber, String token) {
        try {
            List<GithubApiClient.GithubReviewItem> reviews =
                    githubApiClient.fetchPullRequestReviews(repo.getOwner(), repo.getName(), prNumber, token);
            Map<String, String> lastStateByUser = new HashMap<>();
            for (GithubApiClient.GithubReviewItem r : reviews) {
                String login = r.user() != null ? r.user().login() : null;
                if (login == null || r.state() == null) continue;
                if ("COMMENTED".equalsIgnoreCase(r.state())) continue; // 코멘트는 승인 결정에 영향 없음
                lastStateByUser.put(login, r.state());
            }
            return lastStateByUser.values().stream().filter(s -> "APPROVED".equalsIgnoreCase(s)).count();
        } catch (Exception e) {
            log.warn("GitHub PR 리뷰 조회 실패 → pr#{}", prNumber);
            return 0;
        }
    }

    @Transactional
    public void syncAllIssueStatuses(Long repositoryId) {
        List<GithubIssue> issues = githubIssueRepository.findAll().stream()
                .filter(i -> i.getRepository().getId().equals(repositoryId))
                .toList();
        for (GithubIssue issue : issues) {
            threadRepository.findByThreadableTypeAndThreadableId(
                    Thread.THREADABLE_TYPE_GITHUB_ISSUE, issue.getId()
            ).ifPresent(thread -> {
                List<ThreadAttachment> attachments = threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(thread.getId());
                if (!attachments.isEmpty()) {
                    ThreadAttachment att = attachments.get(0);
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = objectMapper.readValue(att.getMeta() != null ? att.getMeta() : "{}", Map.class);
                        boolean changed = false;

                        if (!issue.getState().equals(meta.get("issueStatus"))) {
                            meta.put("issueStatus", issue.getState());
                            changed = true;
                        }

                        // GitHub 이슈 생성 시각 보강 (이전에 생성된 스레드는 이 값이 없어 동기화 시각이 표시되던 문제 해결)
                        // 엔티티의 githubCreatedAt은 UTC LocalDateTime이라 JS가 UTC로 파싱하도록 'Z'를 붙인다.
                        if (issue.getGithubCreatedAt() != null && meta.get("githubCreatedAt") == null) {
                            meta.put("githubCreatedAt", issue.getGithubCreatedAt().toString() + "Z");
                            changed = true;
                        }
                        // 닫힌 이슈의 닫힌 시각도 보강 (닫힘 카드에 닫힌 시각 표시)
                        if (issue.getClosedAt() != null && meta.get("githubClosedAt") == null) {
                            meta.put("githubClosedAt", issue.getClosedAt().toString() + "Z");
                            changed = true;
                        }

                        // 라벨 동기화 (GithubIssue.labels → meta.issueLabels)
                        if (issue.getLabels() != null && !issue.getLabels().isBlank()) {
                            try {
                                @SuppressWarnings("unchecked")
                                List<Map<String, String>> rawLabels = objectMapper.readValue(issue.getLabels(), List.class);
                                List<Map<String, String>> formattedLabels = rawLabels.stream()
                                        .map(l -> {
                                            String color = l.getOrDefault("color", "888888");
                                            return Map.of("name", l.getOrDefault("name", ""), "color", color.startsWith("#") ? color : "#" + color);
                                        })
                                        .toList();
                                meta.put("issueLabels", formattedLabels);
                                changed = true;
                            } catch (Exception ignored) { /* ignore */ }
                        }

                        if (changed) {
                            att.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(att);
                        }
                    } catch (Exception e) {
                        log.warn("이슈 meta 동기화 실패 issueId={}", issue.getId(), e);
                    }
                }
            });
        }
    }

    // 과거(기존) 이슈를 GitHub에서 가져와 DB/스레드로 동기화 (PR sync와 동일 패턴).
    // 웹훅을 놓쳤거나 웹훅 등록 이전에 만들어진 이슈도 모든 워크스페이스 멤버에게 보이도록 공유 채널에 영속화한다.
    public void syncIssuesFromGithub(Long repositoryId, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        validateRepositoryMember(repo, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("GitHub 토큰 없음 → userId={}", userId);
            return;
        }

        Channel channel = getRepoChannel(repo);
        if (channel == null) {
            log.warn("레포지토리 채널을 찾을 수 없음 → repoId={}", repositoryId);
            return;
        }

        List<GithubApiClient.GithubIssueItem> issues;
        try {
            issues = githubApiClient.fetchIssues(repo.getOwner(), repo.getName(), token);
        } catch (Exception e) {
            log.warn("GitHub 이슈 목록 fetch 실패 → repoId={}", repositoryId, e);
            return;
        }

        for (GithubApiClient.GithubIssueItem item : issues) {
            if (item.pullRequest() != null) continue; // 이슈 API가 PR도 반환 → PR 항목 제외
            try {
                syncSingleIssue(repo, channel, repositoryId, item);
            } catch (Exception e) {
                log.warn("이슈 동기화 실패 → issue#{}", item.number(), e);
            }
        }
    }

    @Transactional
    public void syncSingleIssue(GithubRepository repo, Channel channel, Long repositoryId,
                                GithubApiClient.GithubIssueItem item) {
        String githubIssueId = String.valueOf(item.id());
        Optional<GithubIssue> existingOpt =
                githubIssueRepository.findByRepository_IdAndGithubIssueId(repositoryId, githubIssueId);

        if (existingOpt.isPresent()) {
            GithubIssue issue = existingOpt.get();
            // 엔티티 상태/닫힘시각 최신화 (status/시간 sync의 진실의 원천)
            issue.syncFromWebhook(
                    item.title(), item.body(), item.state(), item.htmlUrl(),
                    buildIssueLabelsJson(item.labels()),
                    toLocalDateTime(item.closedAt()), toLocalDateTime(item.updatedAt()));
            githubIssueRepository.save(issue);

            // 스레드가 있으면 attachment meta를 최신 정보로 갱신(생성/닫힘 시각 포함), 없으면 복구 생성
            threadRepository.findByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_ISSUE, issue.getId())
                    .ifPresentOrElse(thread -> {
                        List<ThreadAttachment> atts = threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(thread.getId());
                        if (atts.isEmpty()) return;
                        ThreadAttachment att = atts.get(0);
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> meta = att.getMeta() != null
                                    ? objectMapper.readValue(att.getMeta(), Map.class)
                                    : new HashMap<>();
                            meta.put("issueStatus", item.state());
                            meta.put("issueTitle", item.title());
                            if (item.body() != null) meta.put("issueBody", item.body());
                            if (item.createdAt() != null) meta.put("githubCreatedAt", item.createdAt().toString());
                            if (item.closedAt() != null) meta.put("githubClosedAt", item.closedAt().toString());
                            att.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(att);
                        } catch (Exception e) {
                            log.warn("이슈 meta 갱신 실패 → issue#{}", item.number(), e);
                        }
                    }, () -> createIssueThreadAndAttachment(channel, issue, item));
            return;
        }

        GithubIssue issue = GithubIssue.create(
                repo, channel, githubIssueId, item.number(), item.title(),
                item.body(), item.state(), item.htmlUrl(),
                item.user() != null ? item.user().login() : null,
                buildIssueLabelsJson(item.labels()),
                toLocalDateTime(item.closedAt()),
                toLocalDateTime(item.createdAt()),
                toLocalDateTime(item.updatedAt())
        );
        GithubIssue savedIssue = githubIssueRepository.save(issue);
        createIssueThreadAndAttachment(channel, savedIssue, item);
    }

    private void createIssueThreadAndAttachment(Channel channel, GithubIssue issue,
                                                GithubApiClient.GithubIssueItem item) {
        String content = String.format("Issue #%d opened by %s: %s",
                item.number(),
                item.user() != null ? item.user().login() : "unknown",
                item.title());

        Thread thread = Thread.createBotNotification(
                channel, content, Thread.THREADABLE_TYPE_GITHUB_ISSUE, issue.getId());
        Thread savedThread = threadRepository.save(thread);

        String meta = buildIssueMetaFromItem(issue, item);
        ThreadAttachment attachment = ThreadAttachment.create(
                savedThread, "issue", issue.getId(),
                item.htmlUrl(), "Issue #" + item.number() + ": " + item.title(),
                item.user() != null ? item.user().login() : null,
                meta, null, null, null
        );
        threadAttachmentRepository.save(attachment);
    }

    private String buildIssueLabelsJson(List<GithubApiClient.GithubIssueLabel> labels) {
        if (labels == null || labels.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(
                    labels.stream().map(l -> Map.of("name", l.name(), "color", l.color())).toList());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String buildIssueMetaFromItem(GithubIssue issue, GithubApiClient.GithubIssueItem item) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("id", issue.getId());
        meta.put("issueNumber", item.number());
        meta.put("issueTitle", item.title());
        meta.put("issueStatus", item.state());
        meta.put("issueAuthor", item.user() != null ? item.user().login() : null);
        meta.put("issuePriority", issue.getPriority());
        meta.put("issueType", issue.getIssueType());
        meta.put("issueBody", item.body());
        meta.put("issueAssignees", item.assignees() != null
                ? item.assignees().stream().map(GithubApiClient.GithubPrUser::login).toList()
                : List.of());
        meta.put("issueLabels", item.labels() != null
                ? item.labels().stream().map(l -> Map.of("name", l.name(), "color", "#" + l.color())).toList()
                : List.of());
        meta.put("issueHistory", List.of(Map.of(
                "id", "h1",
                "actor", item.user() != null ? item.user().login() : "unknown",
                "action", "이슈를 생성했습니다",
                "time", item.createdAt() != null ? item.createdAt().toString() : "",
                "eventType", "created"
        )));
        meta.put("githubCreatedAt", item.createdAt() != null ? item.createdAt().toString() : null);
        meta.put("githubClosedAt", item.closedAt() != null ? item.closedAt().toString() : null);
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private void broadcastIssueStatusUpdate(GithubIssue issue, GithubIssueWebhookPayload.IssueDto dto) {
        threadRepository.findByThreadableTypeAndThreadableId(
                Thread.THREADABLE_TYPE_GITHUB_ISSUE, issue.getId()
        ).ifPresent(thread -> {
            List<ThreadAttachment> attachments = threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(thread.getId());
            if (!attachments.isEmpty()) {
                String newMeta = buildIssueMeta(issue, dto);
                attachments.get(0).updateMeta(newMeta);
                threadAttachmentRepository.save(attachments.get(0));
            }

            List<ThreadAttachmentResponse> attachmentResponses = threadAttachmentRepository
                    .findAllByThread_IdOrderByIdAsc(thread.getId())
                    .stream()
                    .map(ThreadAttachmentResponse::from)
                    .toList();

            ChannelMessageResponse response = ChannelMessageResponse.fromBot(
                    thread, "GitHub Bot", attachmentResponses
            );

            messagingTemplate.convertAndSend(
                    "/topic/channels/" + thread.getChannel().getId() + "/events",
                    ChatEventResponse.of(ChatEventType.MESSAGE_UPDATED, response)
            );
        });
    }

    private Channel findChannelForRepo(GithubRepository repo) {
        // 레포지토리 채널은 GithubRepositoryService.findOrCreateRepositoryChannel() 에서 생성됨
        // Thread 엔티티가 Channel.workspace -> Workspace 로드 필요 → 직접 레포 엔티티 활용
        return repo.getWorkspace() != null
                ? getRepoChannel(repo)
                : null;
    }

    private Channel getRepoChannel(GithubRepository repo) {
        // Channel.githubRepository → 해당 레포에 연결된 채널을 JPA를 통해 조회
        return threadRepository.findChannelByGithubRepositoryId(repo.getId()).orElse(null);
    }

    /**
     * 현재 사용자가 레포지토리가 속한 워크스페이스의 활성 멤버인지 검증한다.
     * 멤버가 아니면 FORBIDDEN. (PR 조회/동기화/승인/병합 등 사용자 토큰을 쓰는 작업의 공통 가드)
     */
    private WorkspaceMember validateRepositoryMember(GithubRepository repo, Long userId) {
        if (repo.getWorkspace() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(repo.getWorkspace().getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private void saveLabels(GithubIssue issue, List<GithubIssueWebhookPayload.LabelDto> labelDtos) {
        if (labelDtos == null || labelDtos.isEmpty()) return;
        List<IssueLabel> labels = labelDtos.stream()
                .map(l -> IssueLabel.create(issue, l.name(), l.color()))
                .toList();
        issueLabelRepository.saveAll(labels);
    }

    private void broadcastBotNotification(GithubIssue issue, GithubIssueWebhookPayload.IssueDto dto) {
        Channel channel = issue.getChannel();
        String content = String.format("Issue #%d opened by %s: %s",
                dto.number(),
                dto.user() != null ? dto.user().login() : "unknown",
                dto.title());

        Thread thread = Thread.createBotNotification(
                channel, content, Thread.THREADABLE_TYPE_GITHUB_ISSUE, issue.getId());
        Thread savedThread = threadRepository.save(thread);

        String meta = buildIssueMeta(issue, dto);
        ThreadAttachment attachment = ThreadAttachment.create(
                savedThread, "issue", issue.getId(),
                dto.htmlUrl(), "Issue #" + dto.number() + ": " + dto.title(),
                dto.user() != null ? dto.user().login() : null,
                meta, null, null, null
        );
        ThreadAttachment savedAttachment = threadAttachmentRepository.save(attachment);

        ChannelMessageResponse response = ChannelMessageResponse.fromBot(
                savedThread, "GitHub Bot",
                List.of(ThreadAttachmentResponse.from(savedAttachment))
        );

        messagingTemplate.convertAndSend(
                "/topic/channels/" + channel.getId() + "/events",
                ChatEventResponse.of(ChatEventType.MESSAGE_CREATED, response)
        );
    }

    private String buildIssueMeta(GithubIssue issue, GithubIssueWebhookPayload.IssueDto dto) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("id", issue.getId());
        meta.put("issueNumber", dto.number());
        meta.put("issueTitle", dto.title());
        meta.put("issueStatus", dto.state());
        meta.put("issueAuthor", dto.user() != null ? dto.user().login() : null);
        meta.put("issuePriority", issue.getPriority());
        meta.put("issueType", issue.getIssueType());
        meta.put("issueBody", dto.body());
        meta.put("issueAssignees", dto.assignees() != null
                ? dto.assignees().stream().map(GithubIssueWebhookPayload.UserDto::login).toList()
                : List.of());
        meta.put("issueLabels", dto.labels() != null
                ? dto.labels().stream().map(l -> Map.of("name", l.name(), "color", "#" + l.color())).toList()
                : List.of());
        meta.put("issueHistory", List.of(Map.of(
                "id", "h1",
                "actor", dto.user() != null ? dto.user().login() : "unknown",
                "action", "이슈를 생성했습니다",
                "time", dto.createdAt() != null ? dto.createdAt().toString() : "",
                "eventType", "created"
        )));
        meta.put("githubCreatedAt", dto.createdAt() != null ? dto.createdAt().toString() : null);
        meta.put("githubClosedAt", dto.closedAt() != null ? dto.closedAt().toString() : null);
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            log.warn("이슈 메타 JSON 직렬화 실패", e);
            return "{}";
        }
    }

    private String buildLabelsJson(List<GithubIssueWebhookPayload.LabelDto> labels) {
        if (labels == null || labels.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(labels);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String computeHmacSha256(String secret, byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(payload);
            StringBuilder hex = new StringBuilder();
            for (byte b : raw) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public Map<String, Object> fetchPrBodyFromGithub(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        validateRepositoryMember(repo, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String token = user.getGithubAccessToken();

        Map<String, Object> result = new HashMap<>();
        if (token == null || token.isBlank()) {
            result.put("prBody", "");
            result.put("prCommits", "[]");
            return result;
        }

        try {
            GithubApiClient.GithubPrItem pr = githubApiClient.fetchSinglePullRequest(
                    repo.getOwner(), repo.getName(), prNumber, token);
            if (pr != null) {
                result.put("prBody", pr.body() != null ? pr.body() : "");
                List<GithubApiClient.GithubCommitItem> commits = githubApiClient.fetchPullRequestCommits(
                        repo.getOwner(), repo.getName(), prNumber, token);
                result.put("prCommits", buildCommitsJson(commits));
            } else {
                result.put("prBody", "");
                result.put("prCommits", "[]");
            }
        } catch (Exception e) {
            log.warn("GitHub 단일 PR fetch 실패 → pr#{}", prNumber, e);
            result.put("prBody", "");
            result.put("prCommits", "[]");
        }
        return result;
    }

    public List<Map<String, Object>> fetchPrFilesFromGithub(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        validateRepositoryMember(repo, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            return List.of();
        }

        try {
            return githubApiClient.fetchPullRequestFiles(repo.getOwner(), repo.getName(), prNumber, token)
                    .stream()
                    .map(f -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("filename", f.filename());
                        m.put("status", f.status());
                        m.put("additions", f.additions() != null ? f.additions() : 0);
                        m.put("deletions", f.deletions() != null ? f.deletions() : 0);
                        m.put("patch", f.patch() != null ? f.patch() : "");
                        return m;
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("GitHub PR 파일 목록 fetch 실패 → pr#{}", prNumber, e);
            return List.of();
        }
    }

    @Transactional
    public void approvePullRequest(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        GithubPullRequest pr = githubPullRequestRepository
                .findByRepository_IdAndPrNumber(repositoryId, prNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        // 워크스페이스 멤버가 아니면 승인 불가 — 검증 실패 시 즉시 중단(상태 변경 안 함)
        WorkspaceMember member = validateRepositoryMember(repo, userId);

        // 리뷰 기록 저장(있으면 갱신)
        pullRequestReviewRepository
                .findByGithubPullRequest_IdAndWorkspaceMember_Id(pr.getId(), member.getId())
                .ifPresentOrElse(
                        existing -> existing.updateState("approved"),
                        () -> pullRequestReviewRepository.save(PullRequestReview.create(pr, member, "approved"))
                );

        // PR state는 "approved"로 저장 (syncAllPrStatuses에서 읽는 진실의 원천)
        pr.updateState("approved");
        githubPullRequestRepository.save(pr);

        // meta도 즉시 업데이트
        long approvedCount = pullRequestReviewRepository
                .countByGithubPullRequest_IdAndReviewState(pr.getId(), "approved");
        final long finalApprovedCount = approvedCount;
        Channel approveChannel = getRepoChannel(repo);
        if (approveChannel == null) return;
        threadAttachmentRepository.findAllPrByChannelId(approveChannel.getId())
                .forEach(ta -> {
                    if (ta.getMeta() == null) return;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = objectMapper.readValue(ta.getMeta(), Map.class);
                        Object num = meta.get("prNumber");
                        if (num != null && Integer.parseInt(num.toString()) == prNumber) {
                            // 이미 병합된 PR은 "approved"로 다운그레이드하지 않는다.
                            if (!"merged".equals(meta.get("prStatus"))) {
                                meta.put("prStatus", "approved");
                            }
                            meta.put("approved", (int) finalApprovedCount);
                            ta.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(ta);
                        }
                    } catch (Exception e) {
                        log.warn("PR meta 업데이트 실패 → attachmentId={}", ta.getId(), e);
                    }
                });

        // 승인 상태 변경을 실시간으로 메신저에 반영 (이슈 닫힘과 동일 패턴)
        threadRepository.findByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_PR, pr.getId())
                .ifPresent(thread -> broadcastPrMessageUpdated(thread, approveChannel));

        githubWebhookEventService.onPrReview(
                repo.getWorkspace().getId(), pr.getId(),
                member.getUser().getDisplayName(), "승인",
                repo.getId(), repo.getName(), (long) prNumber);
    }

    @Transactional
    public void processPullRequestReviewEvent(Long repositoryId, GithubPullRequestReviewWebhookPayload payload) {
        if (!"submitted".equals(payload.action())) return;

        GithubPullRequestReviewWebhookPayload.ReviewDto review = payload.review();
        GithubPullRequestReviewWebhookPayload.PullRequestDto prDto = payload.pullRequest();
        if (review == null || prDto == null) return;

        String reviewerLogin = review.user() != null ? review.user().login() : null;
        if (reviewerLogin == null) return;

        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        repo.recordWebhookDelivery("success");

        GithubPullRequest pr = githubPullRequestRepository
                .findByRepository_IdAndPrNumber(repositoryId, prDto.number())
                .orElse(null);
        if (pr == null) {
            log.warn("PR 미등록 상태에서 pull_request_review 이벤트 수신 → skip, repoId={}", repositoryId);
            return;
        }

        Long workspaceId = repo.getWorkspace().getId();
        String state = review.state() != null ? review.state().toLowerCase() : "commented";
        userRepository.findByGithubUsername(reviewerLogin)
                .flatMap(user -> workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, user.getId()))
                .ifPresent(member ->
                        pullRequestReviewRepository.findByGithubPullRequest_IdAndWorkspaceMember_Id(pr.getId(), member.getId())
                                .ifPresentOrElse(
                                        existing -> existing.updateState(state),
                                        () -> pullRequestReviewRepository.save(PullRequestReview.create(pr, member, state))
                                )
                );

        githubWebhookEventService.onPrReview(
                workspaceId, pr.getId(),
                reviewerLogin,
                review.body() != null && !review.body().isBlank() ? review.body() : state,
                repo.getId(), repo.getName(), (long) prDto.number()
        );
    }

    private void syncIssueAssignees(GithubIssue issue,
                                    List<GithubIssueWebhookPayload.UserDto> assignees,
                                    GithubRepository repo) {
        issueAssigneeRepository.deleteAllByGithubIssue_Id(issue.getId());
        if (assignees == null || assignees.isEmpty()) return;
        Long workspaceId = repo.getWorkspace().getId();
        for (GithubIssueWebhookPayload.UserDto assignee : assignees) {
            userRepository.findByGithubUsername(assignee.login())
                    .flatMap(user -> workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, user.getId()))
                    .ifPresent(member -> issueAssigneeRepository.save(IssueAssignee.create(issue, member)));
        }
    }

    private void syncReviewRequests(GithubPullRequest pr,
                                    List<GithubPullRequestWebhookPayload.UserDto> requestedReviewers,
                                    GithubRepository repo) {
        pullRequestReviewRequestRepository.deleteAllByGithubPullRequest_Id(pr.getId());
        if (requestedReviewers == null || requestedReviewers.isEmpty()) return;
        Long workspaceId = repo.getWorkspace().getId();
        for (GithubPullRequestWebhookPayload.UserDto reviewer : requestedReviewers) {
            userRepository.findByGithubUsername(reviewer.login())
                    .flatMap(user -> workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, user.getId()))
                    .ifPresent(member -> pullRequestReviewRequestRepository.save(PullRequestReviewRequest.create(pr, member)));
        }
    }

    public void mergePullRequestOnGithub(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        validateRepositoryMember(repo, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED);
        }

        // GitHub API로 실제 merge
        githubApiClient.mergePullRequest(repo.getOwner(), repo.getName(), prNumber, token);

        // DB 및 meta 업데이트
        updatePrToMerged(repositoryId, prNumber, repo);
    }

    @Transactional
    public void updatePrToMerged(Long repositoryId, int prNumber, GithubRepository repo) {
        GithubPullRequest pr = githubPullRequestRepository
                .findByRepository_IdAndPrNumber(repositoryId, prNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        pr.updateMerged();
        githubPullRequestRepository.save(pr);

        Channel channel = getRepoChannel(repo);
        if (channel == null) return;

        threadAttachmentRepository.findAllPrByChannelId(channel.getId())
                .forEach(ta -> {
                    if (ta.getMeta() == null) return;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = objectMapper.readValue(ta.getMeta(), Map.class);
                        Object num = meta.get("prNumber");
                        if (num != null && Integer.parseInt(num.toString()) == prNumber) {
                            meta.put("prStatus", "merged");
                            meta.put("githubMergedAt", pr.getMergedAt() != null ? pr.getMergedAt().toString() : null);
                            ta.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(ta);
                        }
                    } catch (Exception e) {
                        log.warn("PR merged meta 업데이트 실패 → attachmentId={}", ta.getId(), e);
                    }
                });

        // 병합됨 상태를 실시간으로 메신저에 반영
        threadRepository.findByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_PR, pr.getId())
                .ifPresent(thread -> broadcastPrMessageUpdated(thread, channel));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyReview(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        GithubPullRequest pr = githubPullRequestRepository
                .findByRepository_IdAndPrNumber(repositoryId, prNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(repo.getWorkspace().getId(), userId)
                .orElse(null);

        Map<String, Object> result = new HashMap<>();
        if (member == null) {
            result.put("reviewState", null);
            return result;
        }

        pullRequestReviewRepository
                .findByGithubPullRequest_IdAndWorkspaceMember_Id(pr.getId(), member.getId())
                .ifPresentOrElse(
                        review -> result.put("reviewState", review.getReviewState()),
                        () -> result.put("reviewState", null)
                );
        return result;
    }

    // 이슈 syncAllIssueStatuses와 동일 패턴: DB 엔티티 상태만 읽어서 meta 갱신, GitHub API 호출 없음
    @Transactional
    public void syncAllPrStatuses(Long repositoryId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        Channel channel = getRepoChannel(repo);
        if (channel == null) return;

        List<GithubPullRequest> prs = githubPullRequestRepository
                .findAllByRepository_IdOrderByGithubCreatedAtDesc(repositoryId);

        List<ThreadAttachment> allPrAttachments = threadAttachmentRepository.findAllPrByChannelId(channel.getId());

        for (GithubPullRequest pr : prs) {
            // merged인 경우만 meta를 업그레이드 — "open"으로는 절대 다운그레이드하지 않음
            boolean isMergedInDb = pr.getMergedAt() != null || "merged".equals(pr.getState());
            if (!isMergedInDb) continue; // open/approved 상태는 건드리지 않음

            allPrAttachments.forEach(ta -> {
                        if (ta.getMeta() == null) return;
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> meta = objectMapper.readValue(ta.getMeta(), Map.class);
                            Object num = meta.get("prNumber");
                            if (num == null || Integer.parseInt(num.toString()) != pr.getPrNumber()) return;

                            String currentStatus = (String) meta.getOrDefault("prStatus", "open");
                            if ("merged".equals(currentStatus)) return; // 이미 병합됨 — 스킵

                            // DB에서 merged로 확인된 PR만 meta를 "merged"로 업데이트
                            meta.put("prStatus", "merged");
                            meta.put("githubMergedAt", pr.getMergedAt() != null ? pr.getMergedAt().toString() : null);
                            ta.updateMeta(objectMapper.writeValueAsString(meta));
                            threadAttachmentRepository.save(ta);
                        } catch (Exception e) {
                            log.warn("PR status sync 실패 → prId={}", pr.getId(), e);
                        }
                    });
        }
    }

    public List<Map<String, Object>> getPullRequestsAsMessages(Long repositoryId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        return githubPullRequestRepository
                .findAllByRepository_IdOrderByGithubCreatedAtDesc(repositoryId)
                .stream()
                .map(pr -> buildPrMessageMap(repo, pr))
                .toList();
    }

    public void syncPullRequestsFromGithub(Long repositoryId, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        validateRepositoryMember(repo, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        String token = user.getGithubAccessToken();
        if (token == null || token.isBlank()) {
            log.warn("GitHub 토큰 없음 → userId={}", userId);
            return;
        }

        Channel channel = getRepoChannel(repo);
        if (channel == null) {
            log.warn("레포지토리 채널을 찾을 수 없음 → repoId={}", repositoryId);
            return;
        }

        List<GithubApiClient.GithubPrItem> prs;
        try {
            prs = githubApiClient.fetchPullRequests(repo.getOwner(), repo.getName(), token);
        } catch (Exception e) {
            log.warn("GitHub PR 목록 fetch 실패 → repoId={}", repositoryId, e);
            return;
        }

        for (GithubApiClient.GithubPrItem item : prs) {
            try {
                syncSinglePullRequest(repo, channel, repositoryId, item, token);
            } catch (Exception e) {
                log.warn("PR 동기화 실패 → pr#{}", item.number(), e);
            }
        }
    }

    @Transactional
    public void syncSinglePullRequest(GithubRepository repo, Channel channel, Long repositoryId,
                                       GithubApiClient.GithubPrItem item, String token) {
        // PR 목록 API는 additions/deletions/changed_files를 주지 않으므로 단건 상세로 보강한다.
        // (실패 시 목록 데이터로 그대로 진행)
        try {
            GithubApiClient.GithubPrItem detail =
                    githubApiClient.fetchSinglePullRequest(repo.getOwner(), repo.getName(), item.number(), token);
            if (detail != null) item = detail;
        } catch (Exception e) {
            log.warn("PR 단건 상세 fetch 실패 → pr#{} (목록 데이터로 진행)", item.number());
        }

        String githubPrId = String.valueOf(item.id());
        Optional<GithubPullRequest> existingOpt = githubPullRequestRepository
                .findByRepository_IdAndGithubPrId(repositoryId, githubPrId);

        // 커밋 목록 fetch
        String commitsJsonRaw;
        try {
            List<GithubApiClient.GithubCommitItem> commits = githubApiClient.fetchPullRequestCommits(
                    repo.getOwner(), repo.getName(), item.number(), token);
            commitsJsonRaw = buildCommitsJson(commits);
        } catch (Exception e) {
            log.warn("PR 커밋 fetch 실패 → pr#{}, 커밋 없이 진행", item.number());
            commitsJsonRaw = "[]";
        }
        final String commitsJson = commitsJsonRaw;

            if (existingOpt.isPresent()) {
            GithubPullRequest existingPr = existingOpt.get();
            existingPr.updateCommits(commitsJson);
            existingPr.updateDescription(item.body() != null ? item.body() : "");

            // GitHub에서 merge됐으면 DB에 반영, 아직 open/closed이면 approved 상태는 보존
            boolean githubMerged = Boolean.TRUE.equals(item.merged()) || item.mergedAt() != null;
            if (githubMerged) {
                existingPr.updateMergedAt(toLocalDateTime(item.mergedAt()));
            } else if (!"approved".equals(existingPr.getState())) {
                // approved 상태는 CodeDock이 관리 — GitHub "open"으로 덮어쓰지 않음
                existingPr.updateState(item.state());
            }
            githubPullRequestRepository.save(existingPr);

            // 승인 수 = 내부(앱) 승인 기록과 GitHub APPROVED 리뷰어 수 중 큰 값
            long internalApproved = pullRequestReviewRepository
                    .countByGithubPullRequest_IdAndReviewState(existingPr.getId(), "approved");
            long githubApproved = countGithubApprovedReviewers(repo, item.number(), token);
            long approvedCount = Math.max(internalApproved, githubApproved);

            // prStatus 결정: DB 엔티티 상태가 진실의 원천 (이슈 sync와 동일 패턴)
            final String resolvedStatus;
            if (existingPr.getMergedAt() != null) {
                resolvedStatus = "merged";
            } else if ("approved".equals(existingPr.getState()) || approvedCount > 0) {
                resolvedStatus = "approved";
            } else {
                resolvedStatus = existingPr.getState();
            }
            final long finalApprovedCountSync = approvedCount;

            // 이 PR에 해당하는 ThreadAttachment 찾아서 meta 업데이트
            boolean foundAttachment = false;
            for (ThreadAttachment ta : threadAttachmentRepository.findAllPrByChannelId(channel.getId())) {
                if (ta.getMeta() == null) continue;
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = objectMapper.readValue(ta.getMeta(), Map.class);
                    Object num = meta.get("prNumber");
                    if (num == null || Integer.parseInt(num.toString()) != item.number()) continue;
                    foundAttachment = true;
                    String cur = (String) meta.getOrDefault("prStatus", "open");
                    // 상태(prStatus)만 다운그레이드 보호: merged는 유지, approved는 open으로 내리지 않음.
                    // 그 외 메타(시간/통계/제목 등)는 merged PR이어도 항상 최신으로 갱신한다.
                    String nextStatus = resolvedStatus;
                    if ("merged".equals(cur)) {
                        nextStatus = "merged";
                    } else if ("approved".equals(cur) && "open".equals(resolvedStatus)) {
                        nextStatus = "approved";
                    }
                    meta.put("prStatus", nextStatus);
                    meta.put("approved", (int) finalApprovedCountSync);
                    meta.put("prTitle", item.title());
                    meta.put("prBody", item.body() != null ? item.body() : meta.getOrDefault("prBody", ""));
                    meta.put("prCommits", commitsJson);
                    if (item.additions() != null) meta.put("additions", item.additions());
                    if (item.deletions() != null) meta.put("deletions", item.deletions());
                    if (item.changedFiles() != null) meta.put("filesChanged", item.changedFiles());
                    if (item.createdAt() != null) meta.put("githubCreatedAt", item.createdAt().toString());
                    if (item.mergedAt() != null) meta.put("githubMergedAt", item.mergedAt().toString());
                    ta.updateMeta(objectMapper.writeValueAsString(meta));
                    threadAttachmentRepository.save(ta);
                } catch (Exception e) {
                    log.warn("PR sync meta 업데이트 실패 → attachmentId={}", ta.getId(), e);
                }
            }

            // Thread/ThreadAttachment가 없으면 생성 (webhook 실패 등으로 누락된 경우 복구)
            if (!foundAttachment) {
                boolean threadExists = threadRepository
                        .findByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_PR, existingPr.getId())
                        .isPresent();
                if (!threadExists) {
                    log.info("PR Thread 누락 감지 → 재생성 pr#{}", item.number());
                    createPrThreadAndAttachment(repo, channel, existingPr, item, commitsJson, resolvedStatus, finalApprovedCountSync);
                }
            }
            return;
        }

        GithubPullRequest pr = GithubPullRequest.create(
                repo, channel, githubPrId, item.number(), item.title(), item.body(),
                item.state(),
                item.htmlUrl(),
                item.user() != null ? item.user().login() : null,
                item.head() != null ? item.head().ref() : null,
                item.base() != null ? item.base().ref() : null,
                null,
                item.additions(),
                item.deletions(),
                item.changedFiles(),
                (Boolean.TRUE.equals(item.merged()) || item.mergedAt() != null) ? toLocalDateTime(item.mergedAt()) : null,
                toLocalDateTime(item.createdAt()),
                toLocalDateTime(item.updatedAt()),
                commitsJson
        );
        GithubPullRequest savedPr = githubPullRequestRepository.save(pr);

        String initialStatus = (Boolean.TRUE.equals(item.merged()) || item.mergedAt() != null) ? "merged" : item.state();
        long newApproved = countGithubApprovedReviewers(repo, item.number(), token);
        createPrThreadAndAttachment(repo, channel, savedPr, item, commitsJson, initialStatus, newApproved);
    }

    private void createPrThreadAndAttachment(GithubRepository repo, Channel channel, GithubPullRequest savedPr,
                                              GithubApiClient.GithubPrItem item, String commitsJson,
                                              String prStatus, long approvedCount) {
        String content = String.format("PR #%d opened by %s: %s",
                item.number(),
                item.user() != null ? item.user().login() : "unknown",
                item.title());

        Thread thread = Thread.createBotNotification(
                channel, content, Thread.THREADABLE_TYPE_GITHUB_PR, savedPr.getId());
        Thread savedThread = threadRepository.save(thread);

        Map<String, Object> meta = new HashMap<>();
        meta.put("prNumber", item.number());
        meta.put("prTitle", item.title());
        meta.put("prBody", item.body() != null ? item.body() : "");
        meta.put("prStatus", prStatus);
        meta.put("prAuthor", item.user() != null ? item.user().login() : null);
        meta.put("prUrl", item.htmlUrl());
        meta.put("repository", repo.getName());
        meta.put("branch", item.head() != null ? item.head().ref() : null);
        meta.put("additions", item.additions() != null ? item.additions() : 0);
        meta.put("deletions", item.deletions() != null ? item.deletions() : 0);
        meta.put("filesChanged", item.changedFiles() != null ? item.changedFiles() : 0);
        meta.put("approved", (int) approvedCount);
        meta.put("pending", item.requestedReviewers() != null ? item.requestedReviewers().size() : 0);
        meta.put("aiRisk", "Medium");
        meta.put("passed", 0);
        meta.put("labels", List.of());
        meta.put("prCommits", commitsJson);
        meta.put("githubCreatedAt", item.createdAt() != null ? item.createdAt().toString() : null);
        meta.put("githubMergedAt", item.mergedAt() != null ? item.mergedAt().toString() : null);
        String metaJson;
        try {
            metaJson = objectMapper.writeValueAsString(meta);
        } catch (JsonProcessingException e) {
            metaJson = "{}";
        }

        ThreadAttachment attachment = ThreadAttachment.create(
                savedThread, "pr", savedPr.getId(),
                item.htmlUrl(), "PR #" + item.number() + ": " + item.title(),
                item.user() != null ? item.user().login() : null,
                metaJson, null, null, null
        );
        threadAttachmentRepository.save(attachment);
    }

    private Map<String, Object> buildPrMessageMap(GithubRepository repo, GithubPullRequest pr) {
        long approvedCount = pullRequestReviewRepository
                .countByGithubPullRequest_IdAndReviewState(pr.getId(), "approved");

        String prStatus = resolvePrStatus(pr, approvedCount, pr.getState());

        Map<String, Object> meta = new HashMap<>();
        meta.put("id", pr.getId());
        meta.put("user", "GitHub Bot");
        meta.put("text", "PR #" + pr.getPrNumber() + " opened by " + pr.getAuthor() + ": " + pr.getTitle());
        meta.put("type", "pr");
        meta.put("prNumber", pr.getPrNumber());
        meta.put("prTitle", pr.getTitle());
        meta.put("prBody", pr.getDescription() != null ? pr.getDescription() : "");
        meta.put("prCommits", pr.getCommitsJson() != null ? pr.getCommitsJson() : "[]");
        meta.put("prStatus", prStatus);
        meta.put("prAuthor", pr.getAuthor());
        meta.put("prUrl", pr.getUrl());
        meta.put("repository", repo.getName());
        meta.put("branch", pr.getHeadBranch());
        meta.put("additions", pr.getAdditions());
        meta.put("deletions", pr.getDeletions());
        meta.put("filesChanged", pr.getChangedFilesCount());
        meta.put("approved", (int) approvedCount);
        meta.put("pending", 0);
        meta.put("aiRisk", "Medium");
        meta.put("passed", 0);
        meta.put("labels", List.of());
        meta.put("time", pr.getGithubCreatedAt() != null ? pr.getGithubCreatedAt().toString() : "");
        meta.put("githubCreatedAt", pr.getGithubCreatedAt() != null ? pr.getGithubCreatedAt().toString() : null);
        meta.put("githubMergedAt", pr.getMergedAt() != null ? pr.getMergedAt().toString() : null);
        return meta;
    }
}
