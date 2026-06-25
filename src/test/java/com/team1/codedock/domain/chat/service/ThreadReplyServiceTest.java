package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyUpdateRequest;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.repository.ThreadReplyRepository;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadReplyServiceTest {

    @Mock
    private ThreadReplyRepository threadReplyRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private MentionService mentionService;

    @Mock
    private WorkspaceEventService workspaceEventService;

    @Mock
    private GithubPullRequestRepository githubPullRequestRepository;

    @InjectMocks
    private ThreadReplyService threadReplyService;

    @Test
    @DisplayName("스레드 답글 목록을 생성 순서로 조회한다")
    void getReplies() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "테스터"));
        ThreadReply first = reply(100L, thread, member, "첫 번째 답글", LocalDateTime.of(2026, 6, 9, 10, 0));
        ThreadReply second = reply(101L, thread, member, "두 번째 답글", LocalDateTime.of(2026, 6, 9, 10, 1));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findAllByThread_IdOrderByCreatedAtAscIdAsc(threadId))
                .thenReturn(List.of(first, second));

        List<ThreadReplyResponse> responses = threadReplyService.getReplies(threadId, userId);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).id()).isEqualTo(100L);
        assertThat(responses.get(0).threadId()).isEqualTo(threadId);
        assertThat(responses.get(0).senderMemberId()).isEqualTo(20L);
        assertThat(responses.get(0).senderName()).isEqualTo("테스터");
        assertThat(responses.get(0).content()).isEqualTo("첫 번째 답글");
        assertThat(responses.get(1).id()).isEqualTo(101L);
        assertThat(responses.get(1).content()).isEqualTo("두 번째 답글");
    }

    @Test
    @DisplayName("활성 워크스페이스 멤버이면 스레드 답글을 저장한다")
    void createReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        User senderUser = user("tester", "테스터");
        ReflectionTestUtils.setField(senderUser, "avatarUrl", "https://example.com/reply-sender.png");
        WorkspaceMember member = workspaceMember(20L, senderUser);
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("새 답글");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        ThreadReplyResponse response = threadReplyService.createReply(threadId, userId, request);

        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.threadId()).isEqualTo(threadId);
        assertThat(response.senderMemberId()).isEqualTo(20L);
        assertThat(response.senderName()).isEqualTo("테스터");
        assertThat(response.senderAvatarUrl()).isEqualTo("https://example.com/reply-sender.png");
        assertThat(response.content()).isEqualTo("새 답글");
        assertThat(response.isDeleted()).isFalse();
        verify(workspaceMemberRepository).findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId);
        verify(threadReplyRepository).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
        verify(mentionService).createMentionsForThreadReply(
                org.mockito.ArgumentMatchers.any(ThreadReply.class),
                org.mockito.ArgumentMatchers.eq(member),
                org.mockito.ArgumentMatchers.eq("새 답글")
        );
    }

    @Test
    @DisplayName("다른 사용자의 스레드에 답글을 달면 원글 작성자에게 REPLY 이벤트를 기록한다")
    void createReplyRecordsDashboardReplyEventForThreadOwner() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember owner = workspaceMember(21L, user(101L, "owner", "Owner"));
        WorkspaceMember replier = workspaceMember(20L, user(102L, "tester", "Tester"));
        Thread thread = thread(threadId, channel, owner);
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("답글입니다");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(replier));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        ThreadReplyResponse response = threadReplyService.createReply(threadId, userId, request);

        assertThat(response.id()).isEqualTo(200L);
        verify(workspaceEventService).recordEvent(
                workspaceId,
                WorkspaceEvent.EventType.REPLY,
                "Tester",
                null,
                null,
                10L,
                "답글입니다",
                null,
                null,
                threadId,
                null,
                null,
                101L,
                LocalDateTime.of(2026, 6, 9, 10, 2)
        );
    }

    @Test
    @DisplayName("자기 스레드에 직접 답글을 달면 REPLY 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordReplyEventForSelfReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember member = workspaceMember(20L, user(101L, "tester", "Tester"));
        Thread thread = thread(threadId, channel, member);
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("셀프 답글");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        threadReplyService.createReply(threadId, userId, request);

        verify(workspaceEventService, never()).recordEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(WorkspaceEvent.EventType.REPLY),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("원글 작성자가 없는 스레드에 답글을 달면 REPLY 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordReplyEventWithoutThreadOwner() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember replier = workspaceMember(20L, user(102L, "tester", "Tester"));
        Thread thread = thread(threadId, channel);
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("작성자 없는 스레드 답글");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(replier));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        threadReplyService.createReply(threadId, userId, request);

        verifyReplyEventNotRecorded();
    }

    @Test
    @DisplayName("원글 작성자 사용자 정보가 없으면 REPLY 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordReplyEventWithoutThreadOwnerUser() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember ownerWithoutUser = workspaceMember(21L, null);
        WorkspaceMember replier = workspaceMember(20L, user(102L, "tester", "Tester"));
        Thread thread = thread(threadId, channel, ownerWithoutUser);
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("작성자 사용자 정보 없는 답글");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(replier));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        threadReplyService.createReply(threadId, userId, request);

        verifyReplyEventNotRecorded();
    }

    @Test
    @DisplayName("PR 스레드에 답글을 남기면 target 없는 PR_REVIEW 이벤트를 대시보드에 기록한다")
    void createReplyRecordsPullRequestReviewEventForPrThread() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        GithubRepository repository = repository(70L, workspace, "Test_Repository");
        Channel channel = channel(10L, workspace);
        GithubPullRequest pullRequest = pullRequest(90L, repository, channel, 37, "test", "pr-author");
        WorkspaceMember reviewer = workspaceMember(20L, user(102L, "reviewer", "Reviewer"));
        Thread thread = pullRequestThread(threadId, channel, pullRequest.getId());
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("hello.py L1 확인 부탁드립니다");

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(reviewer));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 30));
                    return saved;
                });
        when(githubPullRequestRepository.findById(90L)).thenReturn(Optional.of(pullRequest));

        threadReplyService.createReply(threadId, userId, request);

        verify(workspaceEventService).recordEvent(
                workspaceId,
                WorkspaceEvent.EventType.PR_REVIEW,
                "Reviewer",
                90L,
                null,
                10L,
                "hello.py L1 확인 부탁드립니다",
                70L,
                "Test_Repository",
                threadId,
                37L,
                null,
                null,
                LocalDateTime.of(2026, 6, 25, 16, 30)
        );
    }

    @Test
    @DisplayName("PR 작성자 GitHub 계정 연결 여부와 관계없이 PR_REVIEW 이벤트를 기록한다")
    void createReplyRecordsPullRequestReviewEventWithoutLookingUpTargetUser() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        GithubRepository repository = repository(70L, workspace, "Test_Repository");
        Channel channel = channel(10L, workspace);
        GithubPullRequest pullRequest = pullRequest(90L, repository, channel, 37, "test", "unknown-author");
        WorkspaceMember reviewer = workspaceMember(20L, user(102L, "reviewer", "Reviewer"));
        Thread thread = pullRequestThread(threadId, channel, pullRequest.getId());

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(reviewer));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 31));
                    return saved;
                });
        when(githubPullRequestRepository.findById(90L)).thenReturn(Optional.of(pullRequest));

        threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("대상 계정이 없어도 이벤트 기록"));

        verify(workspaceEventService).recordEvent(
                workspaceId,
                WorkspaceEvent.EventType.PR_REVIEW,
                "Reviewer",
                90L,
                null,
                10L,
                "대상 계정이 없어도 이벤트 기록",
                70L,
                "Test_Repository",
                threadId,
                37L,
                null,
                null,
                LocalDateTime.of(2026, 6, 25, 16, 31)
        );
    }

    @Test
    @DisplayName("PR_REVIEW 이벤트 작성자 이름은 displayName이 없으면 username으로 대체한다")
    void createReplyRecordsPullRequestReviewEventWithUsernameFallbackActor() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        GithubRepository repository = repository(70L, workspace, "Test_Repository");
        Channel channel = channel(10L, workspace);
        GithubPullRequest pullRequest = pullRequest(90L, repository, channel, 37, "test", "pr-author");
        WorkspaceMember reviewer = workspaceMember(20L, user(102L, "reviewer-username", null));
        Thread thread = pullRequestThread(threadId, channel, pullRequest.getId());

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(reviewer));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 34));
                    return saved;
                });
        when(githubPullRequestRepository.findById(90L)).thenReturn(Optional.of(pullRequest));

        threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("작성자 fallback 확인"));

        verify(workspaceEventService).recordEvent(
                workspaceId,
                WorkspaceEvent.EventType.PR_REVIEW,
                "reviewer-username",
                90L,
                null,
                10L,
                "작성자 fallback 확인",
                70L,
                "Test_Repository",
                threadId,
                37L,
                null,
                null,
                LocalDateTime.of(2026, 6, 25, 16, 34)
        );
    }

    @Test
    @DisplayName("일반 스레드 답글은 PR_REVIEW 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordPullRequestReviewEventForNormalThread() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember replier = workspaceMember(20L, user(102L, "tester", "Tester"));
        Thread thread = thread(threadId, channel);

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(replier));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 32));
                    return saved;
                });

        threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("일반 답글"));

        verify(githubPullRequestRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
        verifyPrReviewEventNotRecorded();
    }

    @Test
    @DisplayName("PR 스레드의 워크스페이스와 PR의 워크스페이스가 다르면 PR_REVIEW 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordPullRequestReviewEventForDifferentWorkspacePullRequest() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long otherWorkspaceId = 99L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Workspace otherWorkspace = workspace(otherWorkspaceId);
        Channel channel = channel(10L, workspace);
        Channel otherChannel = channel(11L, otherWorkspace);
        GithubRepository repository = repository(70L, otherWorkspace, "Other_Repository");
        GithubPullRequest pullRequest = pullRequest(90L, repository, otherChannel, 37, "test", "pr-author");
        WorkspaceMember reviewer = workspaceMember(20L, user(102L, "reviewer", "Reviewer"));
        Thread thread = pullRequestThread(threadId, channel, pullRequest.getId());

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(reviewer));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 35));
                    return saved;
                });
        when(githubPullRequestRepository.findById(90L)).thenReturn(Optional.of(pullRequest));

        threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("다른 워크스페이스 PR 답글"));

        verifyPrReviewEventNotRecorded();
    }

    @Test
    @DisplayName("PR 스레드가 가리키는 PR이 없으면 PR_REVIEW 이벤트를 기록하지 않는다")
    void createReplyDoesNotRecordPullRequestReviewEventWhenPullRequestMissing() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Workspace workspace = workspace(workspaceId);
        Channel channel = channel(10L, workspace);
        WorkspaceMember reviewer = workspaceMember(20L, user(102L, "reviewer", "Reviewer"));
        Thread thread = pullRequestThread(threadId, channel, 999L);

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(reviewer));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 25, 16, 33));
                    return saved;
                });
        when(githubPullRequestRepository.findById(999L)).thenReturn(Optional.empty());

        threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("없는 PR 답글"));

        verifyPrReviewEventNotRecorded();
    }

    @Test
    @DisplayName("답글 이모지를 인코딩해 저장하고 응답과 멘션 입력은 원문을 유지한다")
    void createReplyEncodesEmojiContent() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "테스터"));
        String content = "확인했습니다 👍🔥";
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest(content);

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.save(org.mockito.ArgumentMatchers.any(ThreadReply.class)))
                .thenAnswer(invocation -> {
                    ThreadReply saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 200L);
                    ReflectionTestUtils.setField(saved, "createdAt", LocalDateTime.of(2026, 6, 9, 10, 2));
                    return saved;
                });

        ThreadReplyResponse response = threadReplyService.createReply(threadId, userId, request);

        assertThat(response.content()).isEqualTo(content);
        ArgumentCaptor<ThreadReply> replyCaptor = ArgumentCaptor.forClass(ThreadReply.class);
        verify(threadReplyRepository).save(replyCaptor.capture());
        String storedContent = replyCaptor.getValue().getContent();
        assertThat(storedContent).isNotEqualTo(content);
        assertThat(storedContent).isEqualTo("확인했습니다 [[emoji:like]][[emoji:fire]]");
        assertThat(storedContent).doesNotContain("👍", "🔥");
        assertThat(ChatContentEmojiCodec.decode(storedContent)).isEqualTo(content);
        verify(mentionService).createMentionsForThreadReply(
                org.mockito.ArgumentMatchers.any(ThreadReply.class),
                org.mockito.ArgumentMatchers.eq(member),
                org.mockito.ArgumentMatchers.eq(content)
        );
    }

    @Test
    @DisplayName("존재하지 않는 스레드이면 THREAD_NOT_FOUND 예외가 발생한다")
    void getRepliesWithMissingThread() {
        Long threadId = 999L;
        Long userId = 3L;
        when(entityManager.find(Thread.class, threadId)).thenReturn(null);

        assertThatThrownBy(() -> threadReplyService.getReplies(threadId, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.THREAD_NOT_FOUND);

        verify(workspaceMemberRepository, never()).findByWorkspace_IdAndUser_IdAndIsActiveTrue(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()
        );
    }

    @Test
    @DisplayName("사용자 식별값이 없으면 UNAUTHORIZED 예외가 발생한다")
    void createReplyWithoutUserId() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);

        assertThatThrownBy(() -> threadReplyService.createReply(threadId, null, new ThreadReplyCreateRequest("답글")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(threadReplyRepository, never()).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 FORBIDDEN 예외가 발생한다")
    void createReplyWithForbiddenUser() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> threadReplyService.createReply(threadId, userId, new ThreadReplyCreateRequest("답글")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadReplyRepository, never()).save(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("빈 답글 내용이면 INVALID_INPUT 예외가 발생한다")
    void createReplyWithBlankContent() {
        assertThatThrownBy(() -> threadReplyService.createReply(1L, 3L, new ThreadReplyCreateRequest(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(entityManager, never()).find(org.mockito.ArgumentMatchers.eq(Thread.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("인코딩 저장된 답글은 목록 조회 응답에서 원문 이모지로 복원된다")
    void getRepliesDecodesEmojiContent() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "테스터"));
        String content = "목록 답글 😄";
        ThreadReply reply = reply(
                100L,
                thread,
                member,
                ChatContentEmojiCodec.encode(content),
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findAllByThread_IdOrderByCreatedAtAscIdAsc(threadId))
                .thenReturn(List.of(reply));

        List<ThreadReplyResponse> responses = threadReplyService.getReplies(threadId, userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).content()).isEqualTo(content);
    }

    @Test
    @DisplayName("Reply author can update own reply")
    void updateReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, thread, member, "old reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        ThreadReplyResponse response =
                threadReplyService.updateReply(threadId, 100L, userId, new ThreadReplyUpdateRequest("updated reply"));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo("updated reply");
    }

    @Test
    @DisplayName("답글 수정 이모지는 인코딩해 저장하고 응답은 원문을 반환한다")
    void updateReplyEncodesEmojiContent() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, thread, member, "old reply", LocalDateTime.of(2026, 6, 9, 10, 0));
        String content = "수정 답글 🔧";

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        ThreadReplyResponse response =
                threadReplyService.updateReply(threadId, 100L, userId, new ThreadReplyUpdateRequest(content));

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo(content);
        assertThat(reply.getContent()).isNotEqualTo(content);
        assertThat(reply.getContent()).isEqualTo("수정 답글 [[emoji:fix]]");
        assertThat(reply.getContent()).doesNotContain("🔧");
        assertThat(ChatContentEmojiCodec.decode(reply.getContent())).isEqualTo(content);
    }

    @Test
    @DisplayName("Reply author can delete own reply with soft delete content")
    void deleteReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, thread, member, "reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        ThreadReplyResponse response = threadReplyService.deleteReply(threadId, 100L, userId);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo(ThreadReply.DELETED_REPLY_CONTENT);
        assertThat(response.isDeleted()).isTrue();
        verify(threadReplyRepository, never()).delete(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("Rejects updating reply that belongs to another thread")
    void updateReplyWithDifferentThread() {
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread requestedThread = thread(1L, channel(10L, workspace(workspaceId)));
        Thread anotherThread = thread(2L, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, anotherThread, member, "reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, 1L)).thenReturn(requestedThread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() ->
                threadReplyService.updateReply(1L, 100L, userId, new ThreadReplyUpdateRequest("updated")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Rejects updating another member's reply")
    void updateReplyByAnotherMember() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember requester = workspaceMember(20L, user("requester", "Requester"));
        WorkspaceMember author = workspaceMember(21L, user("author", "Author"));
        ThreadReply reply = reply(100L, thread, author, "reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(requester));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() ->
                threadReplyService.updateReply(threadId, 100L, userId, new ThreadReplyUpdateRequest("updated")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("Rejects updating reply with blank content")
    void updateReplyWithBlankContent() {
        assertThatThrownBy(() -> threadReplyService.updateReply(1L, 100L, 3L, new ThreadReplyUpdateRequest(" ")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(entityManager, never()).find(org.mockito.ArgumentMatchers.eq(Thread.class), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Rejects updating deleted reply")
    void updateDeletedReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, thread, member, ThreadReply.DELETED_REPLY_CONTENT, LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() ->
                threadReplyService.updateReply(threadId, 100L, userId, new ThreadReplyUpdateRequest("restore")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    @DisplayName("Rejects deleting reply that belongs to another thread")
    void deleteReplyWithDifferentThread() {
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread requestedThread = thread(1L, channel(10L, workspace(workspaceId)));
        Thread anotherThread = thread(2L, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));
        ThreadReply reply = reply(100L, anotherThread, member, "reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, 1L)).thenReturn(requestedThread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> threadReplyService.deleteReply(1L, 100L, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(threadReplyRepository, never()).delete(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("Rejects deleting another member's reply")
    void deleteReplyByAnotherMember() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember requester = workspaceMember(20L, user("requester", "Requester"));
        WorkspaceMember author = workspaceMember(21L, user("author", "Author"));
        ThreadReply reply = reply(100L, thread, author, "reply", LocalDateTime.of(2026, 6, 9, 10, 0));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(requester));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.of(reply));

        assertThatThrownBy(() -> threadReplyService.deleteReply(threadId, 100L, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verify(threadReplyRepository, never()).delete(org.mockito.ArgumentMatchers.any(ThreadReply.class));
    }

    @Test
    @DisplayName("Rejects deleting missing reply")
    void deleteMissingReply() {
        Long threadId = 1L;
        Long workspaceId = 2L;
        Long userId = 3L;
        Thread thread = thread(threadId, channel(10L, workspace(workspaceId)));
        WorkspaceMember member = workspaceMember(20L, user("tester", "Tester"));

        when(entityManager.find(Thread.class, threadId)).thenReturn(thread);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(workspaceId, userId))
                .thenReturn(Optional.of(member));
        when(threadReplyRepository.findById(100L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> threadReplyService.deleteReply(threadId, 100L, userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private static ThreadReply reply(
            Long id,
            Thread thread,
            WorkspaceMember member,
            String content,
            LocalDateTime createdAt
    ) {
        ThreadReply reply = ThreadReply.create(thread, member, content);
        ReflectionTestUtils.setField(reply, "id", id);
        ReflectionTestUtils.setField(reply, "createdAt", createdAt);
        return reply;
    }

    private static Thread thread(Long id, Channel channel) {
        Thread thread = newInstance(Thread.class);
        ReflectionTestUtils.setField(thread, "id", id);
        ReflectionTestUtils.setField(thread, "channel", channel);
        return thread;
    }

    private static Thread thread(Long id, Channel channel, WorkspaceMember createdBy) {
        Thread thread = thread(id, channel);
        ReflectionTestUtils.setField(thread, "createdBy", createdBy);
        return thread;
    }

    private static Thread pullRequestThread(Long id, Channel channel, Long pullRequestId) {
        Thread thread = Thread.createBotNotification(
                channel,
                "PR 알림",
                Thread.THREADABLE_TYPE_GITHUB_PR,
                pullRequestId
        );
        ReflectionTestUtils.setField(thread, "id", id);
        return thread;
    }

    private static Channel channel(Long id, Workspace workspace) {
        Channel channel = newInstance(Channel.class);
        ReflectionTestUtils.setField(channel, "id", id);
        ReflectionTestUtils.setField(channel, "workspace", workspace);
        return channel;
    }

    private static Workspace workspace(Long id) {
        Workspace workspace = newInstance(Workspace.class);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember workspaceMember(Long id, User user) {
        WorkspaceMember member = newInstance(WorkspaceMember.class);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "user", user);
        return member;
    }

    private static GithubRepository repository(Long id, Workspace workspace, String name) {
        GithubRepository repository = GithubRepository.create(
                workspace,
                "repo-" + id,
                "team1",
                name,
                "team1/" + name,
                "https://github.com/team1/" + name,
                "테스트 레포지토리",
                false,
                "main"
        );
        ReflectionTestUtils.setField(repository, "id", id);
        return repository;
    }

    private static GithubPullRequest pullRequest(
            Long id,
            GithubRepository repository,
            Channel channel,
            Integer prNumber,
            String title,
            String author
    ) {
        GithubPullRequest pullRequest = GithubPullRequest.create(
                repository,
                channel,
                "github-pr-" + id,
                prNumber,
                title,
                "PR 설명",
                "open",
                "https://github.com/team1/repo/pull/" + prNumber,
                author,
                "feature/test",
                "main",
                "[]",
                1,
                0,
                1,
                null,
                LocalDateTime.of(2026, 6, 25, 9, 0),
                LocalDateTime.of(2026, 6, 25, 10, 0),
                "[]"
        );
        ReflectionTestUtils.setField(pullRequest, "id", id);
        return pullRequest;
    }

    private static User user(String username, String displayName) {
        User user = newInstance(User.class);
        ReflectionTestUtils.setField(user, "username", username);
        ReflectionTestUtils.setField(user, "displayName", displayName);
        return user;
    }

    private static User user(Long id, String username, String displayName) {
        User user = user(username, displayName);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private void verifyReplyEventNotRecorded() {
        verify(workspaceEventService, never()).recordEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(WorkspaceEvent.EventType.REPLY),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private void verifyPrReviewEventNotRecorded() {
        verify(workspaceEventService, never()).recordEvent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(WorkspaceEvent.EventType.PR_REVIEW),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static <T> T newInstance(Class<T> type) {
        try {
            Constructor<T> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to create test entity: " + type.getSimpleName(), e);
        }
    }
}
