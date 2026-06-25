package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.ai.service.AiSummaryService;
import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadAttachment;
import com.team1.codedock.domain.chat.repository.ThreadAttachmentRepository;
import com.team1.codedock.domain.chat.repository.ThreadRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.issue.repository.IssueLabelRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestFileRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubWebhookIssueBoardSyncTest {

    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubIssueRepository githubIssueRepository;
    @Mock private IssueLabelRepository issueLabelRepository;
    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private ThreadRepository threadRepository;
    @Mock private ThreadAttachmentRepository threadAttachmentRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private GithubApiClient githubApiClient;
    @Mock private UserRepository userRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private PullRequestReviewRepository pullRequestReviewRepository;
    @Mock private PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    @Mock private PullRequestFileRepository pullRequestFileRepository;
    @Mock private IssueAssigneeRepository issueAssigneeRepository;
    @Mock private AiSummaryService aiSummaryService;
    @Mock private GithubWebhookEventService githubWebhookEventService;

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
    @DisplayName("GitHub sync에서 처음 발견한 closed 이슈는 작업보드 done 상태로 저장된다")
    void syncSingleIssue_newClosedIssue_savesDoneLocalStatus() {
        Workspace workspace = Workspace.create(User.create("owner@example.com", "pw", "오너"), "팀", "team", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        GithubRepository repository = GithubRepository.create(
                workspace,
                "100",
                "team",
                "repo",
                "team/repo",
                "https://github.com/team/repo",
                null,
                false,
                "main"
        );
        ReflectionTestUtils.setField(repository, "id", 20L);
        Channel channel = Channel.createRepository(workspace, repository, "repo");
        ReflectionTestUtils.setField(channel, "id", 30L);
        GithubApiClient.GithubIssueItem item = new GithubApiClient.GithubIssueItem(
                9001L,
                7,
                "이미 닫힌 이슈",
                "본문",
                "closed",
                "https://github.com/team/repo/issues/7",
                new GithubApiClient.GithubPrUser("octocat"),
                List.of(),
                List.of(),
                Instant.parse("2026-06-25T00:00:00Z"),
                Instant.parse("2026-06-25T00:10:00Z"),
                Instant.parse("2026-06-25T01:00:00Z"),
                null
        );

        when(githubIssueRepository.findByRepository_IdAndGithubIssueId(20L, "9001"))
                .thenReturn(Optional.empty());
        when(githubApiClient.fetchIssueClassification("team", "repo", 7, "ghtoken"))
                .thenReturn(new GithubApiClient.IssueClassification("high", "bug"));
        when(githubIssueRepository.save(any(GithubIssue.class))).thenAnswer(invocation -> {
            GithubIssue issue = invocation.getArgument(0);
            ReflectionTestUtils.setField(issue, "id", 40L);
            return issue;
        });
        when(threadRepository.save(any(Thread.class))).thenAnswer(invocation -> {
            Thread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 50L);
            return thread;
        });
        when(threadAttachmentRepository.save(any(ThreadAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        githubWebhookService.syncSingleIssue(repository, channel, 20L, item, "ghtoken");

        ArgumentCaptor<GithubIssue> issueCaptor = ArgumentCaptor.forClass(GithubIssue.class);
        verify(githubIssueRepository).save(issueCaptor.capture());
        GithubIssue savedIssue = issueCaptor.getValue();
        assertThat(savedIssue.getState()).isEqualTo("closed");
        assertThat(savedIssue.getLocalStatus()).isEqualTo("done");
        assertThat(savedIssue.getClosedAt()).isEqualTo(LocalDateTime.of(2026, 6, 25, 1, 0));
        assertThat(savedIssue.getPriority()).isEqualTo("high");
        assertThat(savedIssue.getIssueType()).isEqualTo("bug");
        verify(githubWebhookEventService).onIssueCreated(
                10L,
                40L,
                "octocat",
                "이미 닫힌 이슈",
                20L,
                "repo",
                30L,
                7L,
                LocalDateTime.of(2026, 6, 25, 9, 0)
        );
    }
}
