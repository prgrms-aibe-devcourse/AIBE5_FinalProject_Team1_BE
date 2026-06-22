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
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.entity.PullRequestReview;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.entity.IssueLabel;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
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

    public void verifySignature(Long repositoryId, String signatureHeader, byte[] rawBody) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        String secret = repo.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            throw new BusinessException(ErrorCode.GITHUB_WEBHOOK_INVALID);
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
            broadcastBotNotification(issue, dto);
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
            broadcastPrBotNotification(repo, channel, savedPr, dto);
        } else {
            GithubPullRequest pr = existing.get();
            if (ACTION_CLOSED.equals(action) || "reopened".equals(action)
                    || "synchronize".equals(action) || "edited".equals(action)) {
                broadcastPrStatusUpdate(pr, repo, channel, dto);
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
                    List<ThreadAttachment> attachments = threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(thread.getId());
                    if (!attachments.isEmpty()) {
                        attachments.get(0).updateMeta(buildPrMeta(repo, dto));
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
                            "/topic/channels/" + channel.getId() + "/events",
                            ChatEventResponse.of(ChatEventType.MESSAGE_UPDATED, response)
                    );
                });
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

    @Transactional
    public void approvePullRequest(Long repositoryId, int prNumber, Long userId) {
        GithubRepository repo = githubRepositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        GithubPullRequest pr = githubPullRequestRepository
                .findByRepository_IdAndPrNumber(repositoryId, prNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        WorkspaceMember member = workspaceMemberRepository
                .findByWorkspace_IdAndUser_IdAndIsActiveTrue(repo.getWorkspace().getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        pullRequestReviewRepository
                .findByGithubPullRequest_IdAndWorkspaceMember_Id(pr.getId(), member.getId())
                .ifPresentOrElse(
                        existing -> existing.updateState("approved"),
                        () -> pullRequestReviewRepository.save(PullRequestReview.create(pr, member, "approved"))
                );

        // PR state를 "approved"로 업데이트
        pr.updateState("approved");

        // 해당 채널의 PR ThreadAttachment meta에서 prStatus를 "approved"로 업데이트
        threadAttachmentRepository.findAllPrByChannelId(pr.getChannel().getId())
                .forEach(ta -> {
                    if (ta.getMeta() == null) return;
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> meta = objectMapper.readValue(ta.getMeta(), Map.class);
                        Object num = meta.get("prNumber");
                        if (num != null && Integer.parseInt(num.toString()) == prNumber) {
                            meta.put("prStatus", "approved");
                            ta.updateMeta(objectMapper.writeValueAsString(meta));
                        }
                    } catch (Exception e) {
                        log.warn("PR meta 업데이트 실패 → attachmentId={}", ta.getId(), e);
                    }
                });
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
            githubPullRequestRepository.save(existingPr);

            // ThreadAttachment meta 최신 데이터로 갱신 (prBody 포함)
            threadRepository.findByThreadableTypeAndThreadableId(
                    Thread.THREADABLE_TYPE_GITHUB_PR, existingPr.getId()
            ).ifPresent(thread -> {
                List<ThreadAttachment> attachments = threadAttachmentRepository
                        .findAllByThread_IdOrderByIdAsc(thread.getId());
                if (!attachments.isEmpty()) {
                    Map<String, Object> updatedMeta = new HashMap<>();
                    updatedMeta.put("prNumber", item.number());
                    updatedMeta.put("prTitle", item.title());
                    updatedMeta.put("prBody", item.body() != null ? item.body() : "");
                    updatedMeta.put("prCommits", commitsJson);
                    updatedMeta.put("prStatus", Boolean.TRUE.equals(item.merged()) ? "merged" : item.state());
                    updatedMeta.put("prAuthor", item.user() != null ? item.user().login() : null);
                    updatedMeta.put("prUrl", item.htmlUrl());
                    updatedMeta.put("repository", repo.getName());
                    updatedMeta.put("branch", item.head() != null ? item.head().ref() : null);
                    updatedMeta.put("additions", item.additions() != null ? item.additions() : 0);
                    updatedMeta.put("deletions", item.deletions() != null ? item.deletions() : 0);
                    updatedMeta.put("filesChanged", item.changedFiles() != null ? item.changedFiles() : 0);
                    updatedMeta.put("approved", 0);
                    updatedMeta.put("pending", item.requestedReviewers() != null ? item.requestedReviewers().size() : 0);
                    updatedMeta.put("aiRisk", "Medium");
                    updatedMeta.put("passed", 0);
                    updatedMeta.put("labels", List.of());
                    try {
                        attachments.get(0).updateMeta(objectMapper.writeValueAsString(updatedMeta));
                        threadAttachmentRepository.save(attachments.get(0));
                    } catch (JsonProcessingException e) {
                        log.warn("PR attachment meta 업데이트 실패 → prId={}", existingPr.getId());
                    }
                }
            });
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
                Boolean.TRUE.equals(item.merged()) ? toLocalDateTime(item.mergedAt()) : null,
                toLocalDateTime(item.createdAt()),
                toLocalDateTime(item.updatedAt()),
                commitsJson
        );
        GithubPullRequest savedPr = githubPullRequestRepository.save(pr);

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
        meta.put("prStatus", Boolean.TRUE.equals(item.merged()) ? "merged" : item.state());
        meta.put("prAuthor", item.user() != null ? item.user().login() : null);
        meta.put("prUrl", item.htmlUrl());
        meta.put("repository", repo.getName());
        meta.put("branch", item.head() != null ? item.head().ref() : null);
        meta.put("additions", item.additions() != null ? item.additions() : 0);
        meta.put("deletions", item.deletions() != null ? item.deletions() : 0);
        meta.put("filesChanged", item.changedFiles() != null ? item.changedFiles() : 0);
        meta.put("approved", 0);
        meta.put("pending", item.requestedReviewers() != null ? item.requestedReviewers().size() : 0);
        meta.put("aiRisk", "Medium");
        meta.put("passed", 0);
        meta.put("labels", List.of());
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
        Map<String, Object> meta = new HashMap<>();
        meta.put("id", pr.getId());
        meta.put("user", "GitHub Bot");
        meta.put("text", "PR #" + pr.getPrNumber() + " opened by " + pr.getAuthor() + ": " + pr.getTitle());
        meta.put("type", "pr");
        meta.put("prNumber", pr.getPrNumber());
        meta.put("prTitle", pr.getTitle());
        meta.put("prBody", pr.getDescription() != null ? pr.getDescription() : "");
        meta.put("prCommits", pr.getCommitsJson() != null ? pr.getCommitsJson() : "[]");
        meta.put("prStatus", pr.getMergedAt() != null ? "merged" : pr.getState());
        meta.put("prAuthor", pr.getAuthor());
        meta.put("prUrl", pr.getUrl());
        meta.put("repository", repo.getName());
        meta.put("branch", pr.getHeadBranch());
        meta.put("additions", pr.getAdditions());
        meta.put("deletions", pr.getDeletions());
        meta.put("filesChanged", pr.getChangedFilesCount());
        meta.put("approved", 0);
        meta.put("pending", 0);
        meta.put("aiRisk", "Medium");
        meta.put("passed", 0);
        meta.put("labels", List.of());
        meta.put("time", pr.getGithubCreatedAt() != null ? pr.getGithubCreatedAt().toString() : "");
        return meta;
    }
}
