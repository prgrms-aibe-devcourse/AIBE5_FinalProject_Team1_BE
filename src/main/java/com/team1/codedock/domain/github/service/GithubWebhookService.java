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
    private final ThreadRepository threadRepository;
    private final ThreadAttachmentRepository threadAttachmentRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

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
}
