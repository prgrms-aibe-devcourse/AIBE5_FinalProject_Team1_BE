package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.github.dto.GithubIssueWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubPullRequestReviewWebhookPayload;
import com.team1.codedock.domain.github.dto.GithubPullRequestWebhookPayload;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.entity.IssueLabel;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.issue.repository.IssueLabelRepository;
import com.team1.codedock.domain.ai.service.AiSummaryService;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestFileRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubWebhookServiceTest {

    @Mock
    private GithubRepositoryRepository githubRepositoryRepository;
    @Mock
    private GithubIssueRepository githubIssueRepository;
    @Mock
    private IssueLabelRepository issueLabelRepository;
    @Mock
    private GithubPullRequestRepository githubPullRequestRepository;
    @Mock
    private ThreadRepository threadRepository;
    @Mock
    private ThreadAttachmentRepository threadAttachmentRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private GithubApiClient githubApiClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock
    private PullRequestReviewRepository pullRequestReviewRepository;
    @Mock
    private PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    @Mock
    private PullRequestFileRepository pullRequestFileRepository;
    @Mock
    private IssueAssigneeRepository issueAssigneeRepository;
    @Mock
    private AiSummaryService aiSummaryService;
    @Mock
    private GithubWebhookEventService githubWebhookEventService;

    private GithubWebhookService githubWebhookService;

    @BeforeEach
    void setUp() {
        githubWebhookService = new GithubWebhookService(
                githubRepositoryRepository,
                githubIssueRepository,
                issueLabelRepository,
                githubPullRequestRepository,
                threadRepository,
                threadAttachmentRepository,
                messagingTemplate,
                new ObjectMapper(),
                githubApiClient,
                userRepository,
                workspaceMemberRepository,
                pullRequestReviewRepository,
                pullRequestReviewRequestRepository,
                pullRequestFileRepository,
                issueAssigneeRepository,
                aiSummaryService,
                githubWebhookEventService
        );
    }

    @Test
    @DisplayName("sync 쿨다운은 첫 요청만 통과시키고 쿨다운 안의 반복 요청은 차단한다")
    void withinSyncCooldown_순차_요청_차단() {
        ConcurrentHashMap<Long, Long> lastSyncByRepo = new ConcurrentHashMap<>();

        boolean firstBlocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);
        boolean secondBlocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);

        assertThat(firstBlocked).isFalse();
        assertThat(secondBlocked).isTrue();
        assertThat(lastSyncByRepo).containsOnlyKeys(20L);
    }

    @Test
    @DisplayName("sync 쿨다운은 repositoryId별로 독립 적용된다")
    void withinSyncCooldown_repository별_독립_적용() {
        ConcurrentHashMap<Long, Long> lastSyncByRepo = new ConcurrentHashMap<>();

        boolean firstRepoBlocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);
        boolean secondRepoBlocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 21L);
        boolean firstRepoRetryBlocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);

        assertThat(firstRepoBlocked).isFalse();
        assertThat(secondRepoBlocked).isFalse();
        assertThat(firstRepoRetryBlocked).isTrue();
        assertThat(lastSyncByRepo).containsOnlyKeys(20L, 21L);
    }

    @Test
    @DisplayName("sync 쿨다운이 지나면 같은 repositoryId도 다시 통과한다")
    void withinSyncCooldown_만료되면_다시_통과() {
        ConcurrentHashMap<Long, Long> lastSyncByRepo = new ConcurrentHashMap<>();
        long expiredAt = System.currentTimeMillis() - 16_000;
        lastSyncByRepo.put(20L, expiredAt);

        boolean blocked = ReflectionTestUtils.invokeMethod(
                githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);

        assertThat(blocked).isFalse();
        assertThat(lastSyncByRepo.get(20L)).isGreaterThan(expiredAt);
    }

    @Test
    @DisplayName("sync 쿨다운은 동시 요청이 몰려도 한 repositoryId에서 하나만 통과시킨다")
    void withinSyncCooldown_동시_요청도_하나만_통과() throws Exception {
        ConcurrentHashMap<Long, Long> lastSyncByRepo = new ConcurrentHashMap<>();
        int requestCount = 32;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch readyLatch = new CountDownLatch(requestCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requestCount);
        AtomicInteger passedCount = new AtomicInteger();
        AtomicInteger blockedCount = new AtomicInteger();

        for (int i = 0; i < requestCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    boolean blocked = ReflectionTestUtils.invokeMethod(
                            githubWebhookService, "withinSyncCooldown", lastSyncByRepo, 20L);
                    if (blocked) {
                        blockedCount.incrementAndGet();
                    } else {
                        passedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    java.lang.Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        assertThat(readyLatch.await(3, TimeUnit.SECONDS)).isTrue();
        startLatch.countDown();
        assertThat(doneLatch.await(3, TimeUnit.SECONDS)).isTrue();
        executorService.shutdownNow();

        assertThat(passedCount.get()).isEqualTo(1);
        assertThat(blockedCount.get()).isEqualTo(requestCount - 1);
        assertThat(lastSyncByRepo).containsOnlyKeys(20L);
    }

    @Test
    @DisplayName("레포지토리가 없으면 GITHUB_REPO_NOT_FOUND 예외가 발생한다")
    void verifySignature_레포_없으면_예외() {
        when(githubRepositoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(99L, null, "body".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("secret이 null인 경우 signature 검증을 건너뛴다")
    void verifySignature_secret_null이면_검증_스킵() {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(20L, null, "body".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_WEBHOOK_INVALID.getMessage());
    }

    @Test
    @DisplayName("secret이 빈 문자열인 경우 signature 검증을 건너뛴다")
    void verifySignature_secret_blank이면_검증_스킵() {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        repo.updateWebhook("1", "", "url", true);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(20L, null, "body".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_WEBHOOK_INVALID.getMessage());
    }

    @Test
    @DisplayName("GitHub repo id lookup uses only active repositories with a webhook secret")
    void findRepositoryIdsByGithubRepoId_activeSignedOnly() {
        Workspace workspace = workspace(10L);
        GithubRepository active = githubRepository(workspace, 20L);
        active.updateWebhook("1", "secret", "url", true);
        GithubRepository blankSecret = githubRepository(workspace, 21L);
        blankSecret.updateWebhook("2", "", "url", true);
        GithubRepository inactive = githubRepository(workspace, 22L);
        inactive.updateWebhook("3", "secret", "url", false);
        GithubRepository neverRegistered = githubRepository(workspace, 23L);

        when(githubRepositoryRepository.findAllByGithubRepoId("100"))
                .thenReturn(List.of(active, blankSecret, inactive, neverRegistered));

        assertThat(githubWebhookService.findRepositoryIdsByGithubRepoId("100"))
                .containsExactly(20L);
    }

    @Test
    @DisplayName("secret이 있고 서명이 일치하면 검증을 통과한다")
    void verifySignature_서명_일치하면_통과() throws Exception {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        repo.updateWebhook("1", "mysecret", "url", true);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        String signature = "sha256=" + hmac("mysecret", body);

        githubWebhookService.verifySignature(20L, signature, body);
    }

    @Test
    @DisplayName("secret이 있는데 signatureHeader가 null이면 GITHUB_WEBHOOK_INVALID 예외가 발생한다")
    void verifySignature_secret있는데_헤더_null이면_예외() {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        repo.updateWebhook("1", "mysecret", "url", true);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(20L, null, "payload".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_WEBHOOK_INVALID.getMessage());
    }

    @Test
    @DisplayName("secret이 있는데 signatureHeader 형식이 잘못됐으면 GITHUB_WEBHOOK_INVALID 예외가 발생한다")
    void verifySignature_secret있는데_헤더_형식_잘못됐으면_예외() {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        repo.updateWebhook("1", "mysecret", "url", true);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(20L, "md5=invalidsignature", "payload".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_WEBHOOK_INVALID.getMessage());
    }

    @Test
    @DisplayName("secret이 있고 서명이 불일치하면 GITHUB_WEBHOOK_INVALID 예외가 발생한다")
    void verifySignature_서명_불일치하면_예외() {
        GithubRepository repo = githubRepository(workspace(10L), 20L);
        repo.updateWebhook("1", "mysecret", "url", true);
        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));

        assertThatThrownBy(() ->
                githubWebhookService.verifySignature(20L, "sha256=wrongsignature", "payload".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_WEBHOOK_INVALID.getMessage());
    }

    @Test
    @DisplayName("GitHub issue opened 웹훅은 봇 메시지를 저장하고 repository 채널로 MESSAGE_CREATED 이벤트를 전송한다")
    void processIssueOpenedEventBroadcastsCreatedBotMessage() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubIssueWebhookPayload payload = issuePayload("opened", 9001L, 7, "로그인 버그", "open");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubIssueRepository.save(any(GithubIssue.class))).thenAnswer(invocation -> {
            GithubIssue issue = invocation.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", 40L);
            return issue;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 22, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 60L);
            ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 6, 22, 10, 1));
            return attachment;
        });

        githubWebhookService.processIssueEvent(20L, payload);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/channels/30/events"), eventCaptor.capture());
        ChatEventResponse<?> event = assertChatEvent(eventCaptor.getValue(), ChatEventType.MESSAGE_CREATED);
        ChannelMessageResponse response = assertChannelMessage(event.payload());

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.channelId()).isEqualTo(30L);
        assertThat(response.senderName()).isEqualTo("GitHub Bot");
        assertThat(response.senderMemberId()).isNull();
        assertThat(response.content()).isEqualTo("Issue #7 opened by octocat: 로그인 버그");
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0).attachmentType()).isEqualTo("issue");
        assertThat(response.attachments().get(0).targetId()).isEqualTo(40L);
        assertThat(response.attachments().get(0).meta()).contains("\"issueNumber\":7", "\"issueStatus\":\"open\"");
        assertThat(repository.getWebhookLastStatus()).isEqualTo("success");
        assertThat(repository.getWebhookLastDeliveryAt()).isNotNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<IssueLabel>> labelsCaptor = ArgumentCaptor.forClass(List.class);
        verify(issueLabelRepository).saveAll(labelsCaptor.capture());
        assertThat(labelsCaptor.getValue()).hasSize(1);
        assertThat(labelsCaptor.getValue().get(0).getName()).isEqualTo("bug");

        verify(githubWebhookEventService).onIssueCreated(10L, 40L, "octocat", "로그인 버그", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("기존 GitHub issue 상태 변경 웹훅은 attachment meta를 갱신하고 MESSAGE_UPDATED 이벤트를 전송한다")
    void processExistingIssueStatusEventBroadcastsUpdatedBotMessage() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubIssue issue = githubIssue(repository, channel, 40L, "open");
        Thread thread = botThread(channel, issue, 50L);
        ThreadAttachment attachment = issueAttachment(thread, issue, 60L, "{\"issueStatus\":\"open\"}");
        GithubIssueWebhookPayload payload = issuePayload("closed", 9001L, 7, "로그인 버그 수정", "closed");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.of(issue));
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L))
                .thenReturn(Optional.of(thread));
        when(threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(50L))
                .thenReturn(List.of(attachment));
        when(threadAttachmentRepository.save(attachment)).thenReturn(attachment);

        githubWebhookService.processIssueEvent(20L, payload);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/channels/30/events"), eventCaptor.capture());
        ChatEventResponse<?> event = assertChatEvent(eventCaptor.getValue(), ChatEventType.MESSAGE_UPDATED);
        ChannelMessageResponse response = assertChannelMessage(event.payload());

        assertThat(response.id()).isEqualTo(50L);
        assertThat(response.channelId()).isEqualTo(30L);
        assertThat(response.senderName()).isEqualTo("GitHub Bot");
        assertThat(response.attachments()).hasSize(1);
        assertThat(response.attachments().get(0).meta())
                .contains("\"issueStatus\":\"closed\"", "\"issueTitle\":\"로그인 버그 수정\"");
        assertThat(issue.getState()).isEqualTo("closed");
        assertThat(repository.getWebhookLastStatus()).isEqualTo("success");
        verify(threadAttachmentRepository).save(attachment);
    }

    @Test
    @DisplayName("등록되지 않은 issue의 non-opened 웹훅은 봇 메시지를 전송하지 않는다")
    void processMissingIssueNonOpenedEventDoesNotBroadcast() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        GithubIssueWebhookPayload payload = issuePayload("edited", 9001L, 7, "로그인 버그", "open");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());

        githubWebhookService.processIssueEvent(20L, payload);

        verify(githubIssueRepository, never()).save(any());
        verifyNoInteractions(issueLabelRepository, threadRepository, threadAttachmentRepository, messagingTemplate);
        assertThat(repository.getWebhookLastStatus()).isEqualTo("success");
    }

    @Test
    @DisplayName("이슈 sync는 쿨다운 안의 반복 호출이면 GitHub fetch를 다시 수행하지 않는다")
    void syncIssuesFromGithub_쿨다운_반복호출_fetch_차단() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of());

        githubWebhookService.syncIssuesFromGithub(20L, 3L);
        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubRepositoryRepository).findById(20L);
        verify(workspaceMemberRepository).findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L);
        verify(userRepository).findById(3L);
        verify(threadRepository).findChannelByGithubRepositoryId(20L);
        verify(githubApiClient).fetchIssues("team", "repo", "ghtoken");
    }

    @Test
    @DisplayName("이슈 sync에서 GitHub 토큰이 없으면 fetch와 대시보드 이벤트 기록을 수행하지 않는다")
    void syncIssuesFromGithub_토큰없음_이벤트기록안함() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn(" ");

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(threadRepository, never()).findChannelByGithubRepositoryId(any());
        verifyNoInteractions(githubApiClient, githubWebhookEventService);
    }

    @Test
    @DisplayName("이슈 sync에서 레포지토리 채널이 없으면 fetch와 대시보드 이벤트 기록을 수행하지 않는다")
    void syncIssuesFromGithub_채널없음_이벤트기록안함() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.empty());

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubApiClient, never()).fetchIssues(any(), any(), any());
        verifyNoInteractions(githubWebhookEventService);
    }

    @Test
    @DisplayName("이슈 sync는 한 항목이 실패해도 다음 이슈를 계속 처리하고 이벤트를 기록한다")
    void syncIssuesFromGithub_일부항목실패_다음이슈_이벤트기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubIssueItem failingIssue = githubIssueItem(8001L, 6, "Broken issue");
        GithubApiClient.GithubIssueItem successIssue = githubIssueItem(9001L, 7, "Recovered next issue");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of(failingIssue, successIssue));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "8001"))
                .thenThrow(new IllegalStateException("첫 번째 이슈 저장 준비 실패"));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification(null, null));
        when(githubIssueRepository.save(any(GithubIssue.class))).thenAnswer(invocation -> {
            GithubIssue issue = invocation.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", 40L);
            return issue;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 60L);
            return attachment;
        });

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubWebhookEventService).onIssueCreated(
                10L, 40L, "octocat", "Recovered next issue", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("이슈 sync로 신규 이슈를 저장하면 대시보드 이벤트를 기록한다")
    void syncIssuesFromGithub_신규이슈_대시보드이벤트_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubIssueItem issueItem = githubIssueItem(9001L, 7, "Sync issue");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of(issueItem));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification(null, null));
        when(githubIssueRepository.save(any(GithubIssue.class))).thenAnswer(invocation -> {
            GithubIssue issue = invocation.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", 40L);
            return issue;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 60L);
            return attachment;
        });

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubWebhookEventService).onIssueCreated(
                10L, 40L, "octocat", "Sync issue", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("이슈 sync에서 GitHub 작성자가 없으면 actorName null로 대시보드 이벤트를 기록한다")
    void syncIssuesFromGithub_작성자없음_actorName_null_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubIssueItem issueItem = githubIssueItemWithoutUser(9001L, 7, "Issue without user");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of(issueItem));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification(null, null));
        when(githubIssueRepository.save(any(GithubIssue.class))).thenAnswer(invocation -> {
            GithubIssue issue = invocation.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", 40L);
            return issue;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 60L);
            return attachment;
        });

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubWebhookEventService).onIssueCreated(
                10L, 40L, null, "Issue without user", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("이슈 sync로 기존 이슈를 보정해도 대시보드 이벤트를 기록한다")
    void syncIssuesFromGithub_기존이슈_대시보드이벤트_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubIssue existingIssue = githubIssue(repository, channel, 40L, "open");
        Thread thread = botThread(channel, existingIssue, 50L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubIssueItem issueItem = githubIssueItem(9001L, 7, "Synced existing issue");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of(issueItem));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.of(existingIssue));
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification(null, null));
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(
                Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L)).thenReturn(Optional.of(thread));
        when(threadAttachmentRepository.findAllByThread_IdOrderByIdAsc(50L)).thenReturn(List.of());

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(githubIssueRepository).save(existingIssue);
        verify(threadRepository, never()).save(any(Thread.class));
        verify(githubWebhookEventService).onIssueCreated(
                10L, 40L, "octocat", "Synced existing issue", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("이슈 sync에서 기존 이슈의 봇 스레드가 없으면 스레드를 복구하고 대시보드 이벤트를 기록한다")
    void syncIssuesFromGithub_기존이슈_스레드없음_복구하고_이벤트기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubIssue existingIssue = githubIssue(repository, channel, 40L, "open");
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubIssueItem issueItem = githubIssueItem(9001L, 7, "Recovered issue thread");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchIssues("team", "repo", "ghtoken")).thenReturn(List.of(issueItem));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.of(existingIssue));
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification(null, null));
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(
                Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L)).thenReturn(Optional.empty());
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 60L);
            return attachment;
        });

        githubWebhookService.syncIssuesFromGithub(20L, 3L);

        verify(threadRepository).save(any(Thread.class));
        verify(threadAttachmentRepository).save(any(ThreadAttachment.class));
        verify(githubWebhookEventService).onIssueCreated(
                10L, 40L, "octocat", "Recovered issue thread", 20L, "repo", 7L);
    }

    @Test
    @DisplayName("PR sync로 신규 PR을 저장하면 대시보드 이벤트를 기록한다")
    void syncPullRequestsFromGithub_신규PR_대시보드이벤트_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubPrItem prItem = githubPrItem(1001L, 1, "Sync PR");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of(prItem));
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 1, "ghtoken")).thenReturn(prItem);
        when(githubApiClient.fetchPullRequestCommits("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.empty());
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(invocation -> {
            GithubPullRequest pr = invocation.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 300L);
            return attachment;
        });

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(githubWebhookEventService).onPrCreated(
                10L, 100L, "octocat", "Sync PR", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR sync에서 GitHub 작성자가 없으면 actorName null로 대시보드 이벤트를 기록한다")
    void syncPullRequestsFromGithub_작성자없음_actorName_null_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubPrItem prItem = githubPrItemWithoutUser(1001L, 1, "PR without user");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of(prItem));
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 1, "ghtoken")).thenReturn(prItem);
        when(githubApiClient.fetchPullRequestCommits("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.empty());
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(invocation -> {
            GithubPullRequest pr = invocation.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 300L);
            return attachment;
        });

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(githubWebhookEventService).onPrCreated(
                10L, 100L, null, "PR without user", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR sync로 기존 PR을 보정해도 대시보드 이벤트를 기록한다")
    void syncPullRequestsFromGithub_기존PR_대시보드이벤트_기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubPullRequest existingPr = githubPullRequest(repository, channel, 100L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubPrItem prItem = githubPrItem(1001L, 1, "Synced existing PR");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of(prItem));
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 1, "ghtoken")).thenReturn(prItem);
        when(githubApiClient.fetchPullRequestCommits("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.of(existingPr));
        when(pullRequestReviewRepository.countByGithubPullRequest_IdAndReviewState(100L, "approved"))
                .thenReturn(0L);
        when(githubApiClient.fetchPullRequestReviews("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(threadAttachmentRepository.findAllPrByChannelId(30L)).thenReturn(List.of());
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(
                Thread.THREADABLE_TYPE_GITHUB_PR, 100L)).thenReturn(Optional.of(mock(Thread.class)));

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(githubPullRequestRepository).save(existingPr);
        verify(threadRepository, never()).save(any(Thread.class));
        verify(aiSummaryService).generateSummaryForWebhook(100L);
        verify(githubWebhookEventService).onPrCreated(
                10L, 100L, "octocat", "Synced existing PR", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR sync에서 GitHub 토큰이 없으면 fetch와 대시보드 이벤트 기록을 수행하지 않는다")
    void syncPullRequestsFromGithub_토큰없음_이벤트기록안함() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn(" ");

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(threadRepository, never()).findChannelByGithubRepositoryId(any());
        verifyNoInteractions(githubApiClient, githubWebhookEventService);
    }

    @Test
    @DisplayName("PR sync는 한 항목이 실패해도 다음 PR을 계속 처리하고 이벤트를 기록한다")
    void syncPullRequestsFromGithub_일부항목실패_다음PR_이벤트기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubPrItem failingPr = githubPrItem(1000L, 1, "Broken PR");
        GithubApiClient.GithubPrItem successPr = githubPrItem(1001L, 2, "Recovered next PR");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of(failingPr, successPr));
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 1, "ghtoken")).thenReturn(failingPr);
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 2, "ghtoken")).thenReturn(successPr);
        when(githubApiClient.fetchPullRequestCommits("team", "repo", 2, "ghtoken")).thenReturn(List.of());
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1000"))
                .thenThrow(new IllegalStateException("첫 번째 PR 저장 준비 실패"));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.empty());
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(invocation -> {
            GithubPullRequest pr = invocation.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 300L);
            return attachment;
        });

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(githubWebhookEventService).onPrCreated(
                10L, 100L, "octocat", "Recovered next PR", 20L, "repo", 2L);
    }

    @Test
    @DisplayName("PR sync에서 기존 PR의 봇 스레드가 없으면 스레드를 복구하고 대시보드 이벤트를 기록한다")
    void syncPullRequestsFromGithub_기존PR_스레드없음_복구하고_이벤트기록() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubPullRequest existingPr = githubPullRequest(repository, channel, 100L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        GithubApiClient.GithubPrItem prItem = githubPrItem(1001L, 1, "Recovered PR thread");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of(prItem));
        when(githubApiClient.fetchSinglePullRequest("team", "repo", 1, "ghtoken")).thenReturn(prItem);
        when(githubApiClient.fetchPullRequestCommits("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.of(existingPr));
        when(pullRequestReviewRepository.countByGithubPullRequest_IdAndReviewState(100L, "approved"))
                .thenReturn(0L);
        when(githubApiClient.fetchPullRequestReviews("team", "repo", 1, "ghtoken")).thenReturn(List.of());
        when(threadAttachmentRepository.findAllPrByChannelId(30L)).thenReturn(List.of());
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(
                Thread.THREADABLE_TYPE_GITHUB_PR, 100L)).thenReturn(Optional.empty());
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 25, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> {
            ThreadAttachment attachment = invocation.getArgument(0);
            ReflectionTestUtils.setField(attachment, "id", 300L);
            return attachment;
        });

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(threadRepository).save(any(Thread.class));
        verify(threadAttachmentRepository).save(any(ThreadAttachment.class));
        verify(aiSummaryService).generateSummaryForWebhook(100L);
        verify(githubWebhookEventService).onPrCreated(
                10L, 100L, "octocat", "Recovered PR thread", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR opened 웹훅 수신 시 PullRequestFile을 저장한다")
    void processPullRequestEvent_opened_PullRequestFile_저장() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repo, 30L);
        GithubPullRequestWebhookPayload payload = prPayload("opened", 1001L, 1, "Feature: Add auth");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001")).thenReturn(Optional.empty());
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(inv -> {
            GithubPullRequest pr = inv.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(inv -> {
            Thread thread = inv.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(inv -> {
            ThreadAttachment att = inv.getArgument(0);
            ReflectionTestUtils.setField(att, "id", 300L);
            ReflectionTestUtils.setField(att, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 1));
            return att;
        });
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(member.getUser()).thenReturn(user);
        when(workspaceMemberRepository.findAllByWorkspace_IdAndIsActiveTrue(10L)).thenReturn(List.of(member));
        when(githubApiClient.fetchPullRequestFiles("team", "repo", 1, "ghtoken"))
                .thenReturn(List.of(new GithubApiClient.GithubPrFileItem("src/Main.java", "modified", 5, 2, 7, "@@ -1 +1 @@")));

        githubWebhookService.processPullRequestEvent(20L, payload);

        verify(pullRequestFileRepository).saveAll(any());
        verify(githubWebhookEventService).onPrCreated(10L, 100L, "octocat", "Feature: Add auth", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR opened 웹훅 수신 시 aiSummaryService.generateSummaryForWebhook를 호출한다")
    void processPullRequestEvent_opened_generateSummaryForWebhook_호출() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repo, 30L);
        GithubPullRequestWebhookPayload payload = prPayload("opened", 1001L, 1, "Feature: Add auth");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001")).thenReturn(Optional.empty());
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(inv -> {
            GithubPullRequest pr = inv.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(inv -> {
            Thread thread = inv.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(inv -> {
            ThreadAttachment att = inv.getArgument(0);
            ReflectionTestUtils.setField(att, "id", 300L);
            ReflectionTestUtils.setField(att, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 1));
            return att;
        });
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(member.getUser()).thenReturn(user);
        when(workspaceMemberRepository.findAllByWorkspace_IdAndIsActiveTrue(10L)).thenReturn(List.of(member));
        when(githubApiClient.fetchPullRequestFiles("team", "repo", 1, "ghtoken"))
                .thenReturn(List.of(new GithubApiClient.GithubPrFileItem("src/Main.java", "modified", 5, 2, 7, "@@ -1 +1 @@")));

        githubWebhookService.processPullRequestEvent(20L, payload);

        verify(aiSummaryService).generateSummaryForWebhook(100L);
    }

    @Test
    @DisplayName("PR sync는 쿨다운 안의 반복 호출이면 GitHub fetch를 다시 수행하지 않는다")
    void syncPullRequestsFromGithub_쿨다운_반복호출_fetch_차단() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubApiClient.fetchPullRequests("team", "repo", "ghtoken")).thenReturn(List.of());

        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);
        githubWebhookService.syncPullRequestsFromGithub(20L, 3L);

        verify(githubRepositoryRepository).findById(20L);
        verify(workspaceMemberRepository).findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L);
        verify(userRepository).findById(3L);
        verify(threadRepository).findChannelByGithubRepositoryId(20L);
        verify(githubApiClient).fetchPullRequests("team", "repo", "ghtoken");
    }

    @Test
    @DisplayName("GitHub 토큰이 없으면 파일 fetch를 스킵하지만 AI 요약은 호출한다")
    void processPullRequestEvent_opened_토큰_없으면_파일_스킵_AI_호출() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repo, 30L);
        GithubPullRequestWebhookPayload payload = prPayload("opened", 1001L, 1, "Feature: Add auth");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001")).thenReturn(Optional.empty());
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(inv -> {
            GithubPullRequest pr = inv.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(inv -> {
            Thread thread = inv.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(inv -> {
            ThreadAttachment att = inv.getArgument(0);
            ReflectionTestUtils.setField(att, "id", 300L);
            ReflectionTestUtils.setField(att, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 1));
            return att;
        });
        // 토큰 없음 → 빈 목록 반환 (기본값)
        when(workspaceMemberRepository.findAllByWorkspace_IdAndIsActiveTrue(10L)).thenReturn(List.of());

        githubWebhookService.processPullRequestEvent(20L, payload);

        verify(githubApiClient, never()).fetchPullRequestFiles(any(), any(), anyInt(), any());
        verify(pullRequestFileRepository, never()).saveAll(any());
        verify(aiSummaryService).generateSummaryForWebhook(100L);
    }

    @Test
    @DisplayName("GitHub API 파일 fetch가 실패해도 AI 요약은 호출한다")
    void processPullRequestEvent_opened_파일_fetch_실패해도_AI_호출() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repo, 30L);
        GithubPullRequestWebhookPayload payload = prPayload("opened", 1001L, 1, "Feature: Add auth");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001")).thenReturn(Optional.empty());
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));
        when(githubPullRequestRepository.save(any(GithubPullRequest.class))).thenAnswer(inv -> {
            GithubPullRequest pr = inv.getArgument(0);
            ReflectionTestUtils.setField(pr, "id", 100L);
            return pr;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(inv -> {
            Thread thread = inv.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 200L);
            ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 0));
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(inv -> {
            ThreadAttachment att = inv.getArgument(0);
            ReflectionTestUtils.setField(att, "id", 300L);
            ReflectionTestUtils.setField(att, "createdAt", LocalDateTime.of(2026, 6, 23, 10, 1));
            return att;
        });
        WorkspaceMember member = mock(WorkspaceMember.class);
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("ghtoken");
        when(member.getUser()).thenReturn(user);
        when(workspaceMemberRepository.findAllByWorkspace_IdAndIsActiveTrue(10L)).thenReturn(List.of(member));
        when(githubApiClient.fetchPullRequestFiles("team", "repo", 1, "ghtoken"))
                .thenThrow(new RuntimeException("GitHub API 오류"));

        githubWebhookService.processPullRequestEvent(20L, payload);

        verify(pullRequestFileRepository, never()).saveAll(any());
        verify(aiSummaryService).generateSummaryForWebhook(100L);
    }

    @Test
    @DisplayName("PR 승인 시 onPrReview 이벤트를 발행한다")
    void approvePullRequest_onPrReview_이벤트_발행() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);

        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getId()).thenReturn(100L);

        WorkspaceMember member = mock(WorkspaceMember.class);
        User memberUser = mock(User.class);
        when(memberUser.getDisplayName()).thenReturn("테스터");
        when(member.getId()).thenReturn(30L);
        when(member.getUser()).thenReturn(memberUser);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndPrNumber(20L, 1)).thenReturn(Optional.of(pr));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(pullRequestReviewRepository.findByGithubPullRequest_IdAndWorkspaceMember_Id(100L, 30L))
                .thenReturn(Optional.empty());
        when(pullRequestReviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestReviewRepository.countByGithubPullRequest_IdAndReviewState(100L, "approved"))
                .thenReturn(1L);
        when(githubPullRequestRepository.save(pr)).thenReturn(pr);
        Channel approveChannel = mock(Channel.class);
        when(approveChannel.getId()).thenReturn(30L);
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(approveChannel));
        when(threadAttachmentRepository.findAllPrByChannelId(30L)).thenReturn(List.of());
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(any(), eq(100L))).thenReturn(Optional.empty());

        githubWebhookService.approvePullRequest(20L, 1, 3L);

        verify(githubWebhookEventService).onPrReview(10L, 100L, "테스터", "승인", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR 승인 시 githubUsername이 있으면 githubUsername을 actorName으로 사용한다")
    void approvePullRequest_githubUsername있으면_actorName으로_사용() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);

        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getId()).thenReturn(100L);

        WorkspaceMember member = mock(WorkspaceMember.class);
        User memberUser = mock(User.class);
        when(memberUser.getGithubUsername()).thenReturn("githubuser");
        when(member.getId()).thenReturn(30L);
        when(member.getUser()).thenReturn(memberUser);

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndPrNumber(20L, 1)).thenReturn(Optional.of(pr));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 3L))
                .thenReturn(Optional.of(member));
        when(pullRequestReviewRepository.findByGithubPullRequest_IdAndWorkspaceMember_Id(100L, 30L))
                .thenReturn(Optional.empty());
        when(pullRequestReviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pullRequestReviewRepository.countByGithubPullRequest_IdAndReviewState(100L, "approved"))
                .thenReturn(1L);
        when(githubPullRequestRepository.save(pr)).thenReturn(pr);
        Channel approveChannel = mock(Channel.class);
        when(approveChannel.getId()).thenReturn(30L);
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(approveChannel));
        when(threadAttachmentRepository.findAllPrByChannelId(30L)).thenReturn(List.of());
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(any(), eq(100L))).thenReturn(Optional.empty());

        githubWebhookService.approvePullRequest(20L, 1, 3L);

        verify(githubWebhookEventService).onPrReview(10L, 100L, "githubuser", "승인", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR 리뷰 submitted 이벤트는 onPrReview 이벤트를 발행한다")
    void processPullRequestReviewEvent_submitted_onPrReview_이벤트_발행() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getId()).thenReturn(100L);

        GithubPullRequestReviewWebhookPayload payload = new GithubPullRequestReviewWebhookPayload(
                "submitted",
                new GithubPullRequestReviewWebhookPayload.ReviewDto(1L,
                        new GithubPullRequestReviewWebhookPayload.UserDto("reviewer"), "LGTM", "approved"),
                new GithubPullRequestReviewWebhookPayload.PullRequestDto(1001L, 1, "Feature",
                        new GithubPullRequestReviewWebhookPayload.UserDto("author"))
        );

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndPrNumber(20L, 1)).thenReturn(Optional.of(pr));

        githubWebhookService.processPullRequestReviewEvent(20L, payload);

        verify(githubWebhookEventService).onPrReview(10L, 100L, "reviewer", "LGTM", 20L, "repo", 1L);
    }

    @Test
    @DisplayName("PR 리뷰 이벤트 action이 submitted가 아니면 onPrReview를 호출하지 않는다")
    void processPullRequestReviewEvent_submitted아니면_스킵() {
        GithubPullRequestReviewWebhookPayload payload = new GithubPullRequestReviewWebhookPayload(
                "dismissed",
                new GithubPullRequestReviewWebhookPayload.ReviewDto(1L,
                        new GithubPullRequestReviewWebhookPayload.UserDto("reviewer"), "body", "dismissed"),
                new GithubPullRequestReviewWebhookPayload.PullRequestDto(1001L, 1, "Feature",
                        new GithubPullRequestReviewWebhookPayload.UserDto("author"))
        );

        githubWebhookService.processPullRequestReviewEvent(20L, payload);

        verifyNoInteractions(githubWebhookEventService);
        verifyNoInteractions(githubRepositoryRepository);
    }

    @Test
    @DisplayName("issue assigned 웹훅은 기존 assignee를 삭제하고 새 assignee를 동기화한다")
    void processIssueEvent_assigned_issueAssignee_재동기화() {
        Workspace workspace = workspace(10L);
        GithubRepository repository = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repository, 30L);
        GithubIssue issue = githubIssue(repository, channel, 40L, "open");
        GithubIssueWebhookPayload payload = issuePayload("assigned", 9001L, 7, "로그인 버그", "open");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repository));
        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.of(issue));
        when(threadRepository.findFirstThreadByThreadableTypeAndThreadableId(Thread.THREADABLE_TYPE_GITHUB_ISSUE, 40L))
                .thenReturn(Optional.empty());

        githubWebhookService.processIssueEvent(20L, payload);

        verify(issueAssigneeRepository).deleteAllByGithubIssue_Id(40L);
    }

    @Test
    @DisplayName("PR review_requested 웹훅은 기존 리뷰 요청을 삭제하고 재동기화한다")
    void processPullRequestEvent_reviewRequested_리뷰요청_재동기화() {
        Workspace workspace = workspace(10L);
        GithubRepository repo = githubRepository(workspace, 20L);
        Channel channel = repositoryChannel(workspace, repo, 30L);
        GithubPullRequest pr = mock(GithubPullRequest.class);
        when(pr.getId()).thenReturn(100L);
        GithubPullRequestWebhookPayload payload = prPayload("review_requested", 1001L, 1, "Feature: Add auth");

        when(githubRepositoryRepository.findById(20L)).thenReturn(Optional.of(repo));
        when(githubPullRequestRepository.findByRepository_IdAndGithubPrId(20L, "1001"))
                .thenReturn(Optional.of(pr));
        when(threadRepository.findChannelByGithubRepositoryId(20L)).thenReturn(Optional.of(channel));

        githubWebhookService.processPullRequestEvent(20L, payload);

        verify(pullRequestReviewRequestRepository).deleteAllByGithubPullRequest_Id(100L);
    }

    private static ChatEventResponse<?> assertChatEvent(Object value, ChatEventType expectedType) {
        assertThat(value).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) value;
        assertThat(event.type()).isEqualTo(expectedType);
        return event;
    }

    private static ChannelMessageResponse assertChannelMessage(Object value) {
        assertThat(value).isInstanceOf(ChannelMessageResponse.class);
        return (ChannelMessageResponse) value;
    }

    private static String hmac(String secret, byte[] payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] raw = mac.doFinal(payload);
        StringBuilder hex = new StringBuilder();
        for (byte b : raw) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static GithubPullRequestWebhookPayload prPayload(String action, long prId, int prNumber, String title) {
        GithubPullRequestWebhookPayload.PullRequestDto pr = new GithubPullRequestWebhookPayload.PullRequestDto(
                prId, prNumber, title, "PR 본문", "open",
                "https://github.com/team/repo/pull/" + prNumber,
                new GithubPullRequestWebhookPayload.UserDto("octocat"),
                List.of(), List.of(),
                new GithubPullRequestWebhookPayload.HeadDto("feature-branch"),
                new GithubPullRequestWebhookPayload.BaseDto("main"),
                5, 2, 3, false, null,
                Instant.parse("2026-06-23T00:00:00Z"),
                Instant.parse("2026-06-23T00:00:00Z")
        );
        GithubPullRequestWebhookPayload.RepositoryDto repository =
                new GithubPullRequestWebhookPayload.RepositoryDto(100L, "repo", "team/repo");
        return new GithubPullRequestWebhookPayload(action, pr, repository);
    }

    private static GithubIssueWebhookPayload issuePayload(
            String action,
            long issueId,
            int issueNumber,
            String title,
            String state
    ) {
        GithubIssueWebhookPayload.IssueDto issue = new GithubIssueWebhookPayload.IssueDto(
                issueId,
                issueNumber,
                title,
                "본문",
                state,
                "https://github.com/team/repo/issues/" + issueNumber,
                new GithubIssueWebhookPayload.UserDto("octocat"),
                List.of(new GithubIssueWebhookPayload.LabelDto("bug", "ff0000")),
                List.of(new GithubIssueWebhookPayload.UserDto("assignee")),
                Instant.parse("2026-06-22T00:00:00Z"),
                Instant.parse("2026-06-22T01:00:00Z"),
                "closed".equals(state) ? Instant.parse("2026-06-22T02:00:00Z") : null
        );
        GithubIssueWebhookPayload.RepositoryDto repository =
                new GithubIssueWebhookPayload.RepositoryDto(100L, "repo", "team/repo");
        return new GithubIssueWebhookPayload(action, issue, repository);
    }

    private static GithubApiClient.GithubIssueItem githubIssueItem(long issueId, int issueNumber, String title) {
        return new GithubApiClient.GithubIssueItem(
                issueId,
                issueNumber,
                title,
                "이슈 본문",
                "open",
                "https://github.com/team/repo/issues/" + issueNumber,
                new GithubApiClient.GithubPrUser("octocat"),
                List.of(),
                List.of(),
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z"),
                null,
                null
        );
    }

    private static GithubApiClient.GithubIssueItem githubIssueItemWithoutUser(long issueId, int issueNumber, String title) {
        return new GithubApiClient.GithubIssueItem(
                issueId,
                issueNumber,
                title,
                "이슈 본문",
                "open",
                "https://github.com/team/repo/issues/" + issueNumber,
                null,
                List.of(),
                List.of(),
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z"),
                null,
                null
        );
    }

    private static GithubApiClient.GithubPrItem githubPrItem(long prId, int prNumber, String title) {
        return new GithubApiClient.GithubPrItem(
                prId,
                prNumber,
                title,
                "PR 본문",
                "open",
                "https://github.com/team/repo/pull/" + prNumber,
                new GithubApiClient.GithubPrUser("octocat"),
                List.of(),
                new GithubApiClient.GithubPrBranch("feature-branch"),
                new GithubApiClient.GithubPrBranch("main"),
                3,
                1,
                2,
                false,
                null,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z")
        );
    }

    private static GithubApiClient.GithubPrItem githubPrItemWithoutUser(long prId, int prNumber, String title) {
        return new GithubApiClient.GithubPrItem(
                prId,
                prNumber,
                title,
                "PR 본문",
                "open",
                "https://github.com/team/repo/pull/" + prNumber,
                null,
                List.of(),
                new GithubApiClient.GithubPrBranch("feature-branch"),
                new GithubApiClient.GithubPrBranch("main"),
                3,
                1,
                2,
                false,
                null,
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:00:00Z")
        );
    }

    private static Workspace workspace(Long id) {
        User owner = User.create("owner@example.com", "hashed", "owner");
        Workspace workspace = Workspace.create(owner, "팀", "team-" + id, null);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static GithubRepository githubRepository(Workspace workspace, Long id) {
        GithubRepository repository = GithubRepository.create(
                workspace,
                "100",
                "team",
                "repo",
                "team/repo",
                "https://github.com/team/repo",
                "repository",
                false,
                "main"
        );
        ReflectionTestUtils.setField(repository, "id", id);
        return repository;
    }

    private static Channel repositoryChannel(Workspace workspace, GithubRepository repository, Long id) {
        Channel channel = Channel.createRepository(workspace, repository, "repo");
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private static GithubIssue githubIssue(GithubRepository repository, Channel channel, Long id, String state) {
        GithubIssue issue = GithubIssue.create(
                repository,
                channel,
                "9001",
                7,
                "로그인 버그",
                "본문",
                state,
                "https://github.com/team/repo/issues/7",
                "octocat",
                "[]",
                null,
                LocalDateTime.of(2026, 6, 22, 0, 0),
                LocalDateTime.of(2026, 6, 22, 1, 0)
        );
        ReflectionTestUtils.setField(issue, "id", id);
        return issue;
    }

    private static GithubPullRequest githubPullRequest(GithubRepository repository, Channel channel, Long id) {
        GithubPullRequest pullRequest = GithubPullRequest.create(
                repository,
                channel,
                "1001",
                1,
                "기존 PR",
                "본문",
                "open",
                "https://github.com/team/repo/pull/1",
                "octocat",
                "feature-branch",
                "main",
                null,
                3,
                1,
                2,
                null,
                LocalDateTime.of(2026, 6, 22, 0, 0),
                LocalDateTime.of(2026, 6, 22, 1, 0),
                "[]"
        );
        ReflectionTestUtils.setField(pullRequest, "id", id);
        return pullRequest;
    }

    private static Thread botThread(Channel channel, GithubIssue issue, Long id) {
        Thread thread = Thread.createBotNotification(
                channel,
                "Issue #7 opened by octocat: 로그인 버그",
                Thread.THREADABLE_TYPE_GITHUB_ISSUE,
                issue.getId()
        );
        ReflectionTestUtils.setField(thread, "id", id);
        ReflectionTestUtils.setField(thread, "createdAt", LocalDateTime.of(2026, 6, 22, 10, 0));
        return thread;
    }

    private static ThreadAttachment issueAttachment(Thread thread, GithubIssue issue, Long id, String meta) {
        ThreadAttachment attachment = ThreadAttachment.create(
                thread,
                "issue",
                issue.getId(),
                issue.getUrl(),
                "Issue #7: 로그인 버그",
                "octocat",
                meta,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(attachment, "id", id);
        ReflectionTestUtils.setField(attachment, "createdAt", LocalDateTime.of(2026, 6, 22, 10, 1));
        return attachment;
    }
}
