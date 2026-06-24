package com.team1.codedock.domain.dashboard.service;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.dashboard.dto.DashboardSummaryResponse;
import com.team1.codedock.domain.dashboard.dto.WorkspaceDashboardResponse;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRepository;
import com.team1.codedock.domain.pr.repository.PullRequestReviewRequestRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.domain.workspace.entity.WorkspaceEventReadStatus;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventReadStatusRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceEventRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private IssueAssigneeRepository issueAssigneeRepository;
    @Mock private GithubPullRequestRepository githubPullRequestRepository;
    @Mock private PullRequestReviewRequestRepository pullRequestReviewRequestRepository;
    @Mock private PullRequestReviewRepository pullRequestReviewRepository;
    @Mock private WorkspaceEventRepository workspaceEventRepository;
    @Mock private WorkspaceEventReadStatusRepository workspaceEventReadStatusRepository;

    @InjectMocks
    private DashboardService dashboardService;

    // ── getSummary ────────────────────────────────────────────────────────

    @Test
    @DisplayName("전체 워크스페이스의 이슈/PR/리뷰 통계를 배치 조회하여 반환한다")
    void getSummary_성공() {
        User user = user(1L, "octocat");
        Workspace ws1 = workspace(10L, "워크스페이스1");
        Workspace ws2 = workspace(20L, "워크스페이스2");
        WorkspaceMember m1 = membership(user, ws1);
        WorkspaceMember m2 = membership(user, ws2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m1, m2));
        when(issueAssigneeRepository.countOpenByUserIdAndWorkspaceIdIn(1L, List.of(10L, 20L))).thenReturn(4L);
        when(pullRequestReviewRequestRepository.countByUserIdAndWorkspaceIdIn(1L, List.of(10L, 20L))).thenReturn(3L);
        when(githubPullRequestRepository.countOpenByAuthorAndWorkspaceIdIn("octocat", List.of(10L, 20L))).thenReturn(3L);
        when(pullRequestReviewRepository.countOnOpenPrsByAuthorAndWorkspaceIdIn("octocat", List.of(10L, 20L))).thenReturn(8L);

        DashboardSummaryResponse result = dashboardService.getSummary(1L);

        assertThat(result.openIssueCount()).isEqualTo(4L);
        assertThat(result.openPrCount()).isEqualTo(3L);
        assertThat(result.reviewRequestCount()).isEqualTo(3L);
        assertThat(result.receivedReviewCount()).isEqualTo(8L);
    }

    @Test
    @DisplayName("githubUsername이 없으면 PR/리뷰 수는 0으로 반환한다")
    void getSummary_githubUsername없으면_PR관련_0() {
        User user = user(1L, null);
        WorkspaceMember m = membership(user, workspace(10L, "워크스페이스"));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(issueAssigneeRepository.countOpenByUserIdAndWorkspaceIdIn(1L, List.of(10L))).thenReturn(3L);
        when(pullRequestReviewRequestRepository.countByUserIdAndWorkspaceIdIn(1L, List.of(10L))).thenReturn(2L);

        DashboardSummaryResponse result = dashboardService.getSummary(1L);

        assertThat(result.openIssueCount()).isEqualTo(3L);
        assertThat(result.openPrCount()).isEqualTo(0L);
        assertThat(result.reviewRequestCount()).isEqualTo(2L);
        assertThat(result.receivedReviewCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("사용자가 없으면 NOT_FOUND 예외가 발생한다")
    void getSummary_유저없으면_NOT_FOUND() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getSummary(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ── getWorkspaceStats ─────────────────────────────────────────────────

    @Test
    @DisplayName("워크스페이스별 이슈/PR/리뷰 통계를 GROUP BY 배치 조회하여 반환한다")
    void getWorkspaceStats_성공() {
        User user = user(1L, "octocat");
        Workspace ws = workspace(10L, "프로젝트");
        WorkspaceMember m = membership(user, ws);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrueWithWorkspace(1L)).thenReturn(List.of(m));
        when(issueAssigneeRepository.countOpenGroupByWorkspaceId(1L, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 2L}));
        when(pullRequestReviewRequestRepository.countGroupByWorkspaceId(1L, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 1L}));
        when(githubPullRequestRepository.countOpenGroupByWorkspaceId("octocat", List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 3L}));
        when(pullRequestReviewRepository.countOnOpenPrsGroupByWorkspaceId("octocat", List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 4L}));

        List<WorkspaceDashboardResponse> result = dashboardService.getWorkspaceStats(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).workspaceId()).isEqualTo(10L);
        assertThat(result.get(0).workspaceName()).isEqualTo("프로젝트");
        assertThat(result.get(0).openIssueCount()).isEqualTo(2L);
        assertThat(result.get(0).openPrCount()).isEqualTo(3L);
        assertThat(result.get(0).reviewRequestCount()).isEqualTo(1L);
        assertThat(result.get(0).receivedReviewCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("githubUsername이 없으면 워크스페이스 통계에서 PR/리뷰 수는 0이다")
    void getWorkspaceStats_githubUsername없으면_PR관련_0() {
        User user = user(1L, null);
        Workspace ws = workspace(10L, "프로젝트");
        WorkspaceMember m = membership(user, ws);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrueWithWorkspace(1L)).thenReturn(List.of(m));
        when(issueAssigneeRepository.countOpenGroupByWorkspaceId(1L, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 2L}));
        when(pullRequestReviewRequestRepository.countGroupByWorkspaceId(1L, List.of(10L)))
                .thenReturn(List.<Object[]>of(new Object[]{10L, 1L}));

        List<WorkspaceDashboardResponse> result = dashboardService.getWorkspaceStats(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).openPrCount()).isEqualTo(0L);
        assertThat(result.get(0).receivedReviewCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("사용자가 없으면 getWorkspaceStats에서 NOT_FOUND 예외가 발생한다")
    void getWorkspaceStats_유저없으면_NOT_FOUND() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getWorkspaceStats(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ── getEvents ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("본인이 생성한 PR_CREATED/ISSUE_CREATED 이벤트도 활동 로그로 반환한다")
    void getEvents_성공_브로드캐스트_이벤트_본인_포함() {
        User user = user(1L, "octocat");
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceMember m = membership(user, ws);

        WorkspaceEvent myPrEvent = event(ws, WorkspaceEvent.EventType.PR_CREATED, "octocat", null);
        ReflectionTestUtils.setField(myPrEvent, "id", 1L);
        WorkspaceEvent otherPrEvent = event(ws, WorkspaceEvent.EventType.PR_CREATED, "alice", null);
        ReflectionTestUtils.setField(otherPrEvent, "id", 2L);
        WorkspaceEvent replyEvent = event(ws, WorkspaceEvent.EventType.REPLY, "alice", 1L);
        ReflectionTestUtils.setField(replyEvent, "id", 3L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findDashboardEvents(
                eq(List.of(10L)), eq(1L),
                eq(List.of(WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED)),
                eq(List.of(WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION)),
                any(Pageable.class)
        )).thenReturn(List.of(myPrEvent, otherPrEvent, replyEvent));
        when(workspaceEventReadStatusRepository.findReadEventIdsByUserIdAndEventIds(1L, List.of(1L, 2L, 3L)))
                .thenReturn(Set.of());

        List<DashboardEventResponse> result = dashboardService.getEvents(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).type()).isEqualTo("PR_CREATED");
        assertThat(result.get(0).actorName()).isEqualTo("octocat");
        assertThat(result.get(1).type()).isEqualTo("PR_CREATED");
        assertThat(result.get(1).actorName()).isEqualTo("alice");
        assertThat(result.get(2).type()).isEqualTo("REPLY");
    }

    @Test
    @DisplayName("이벤트 조회 시 read status가 있는 이벤트만 읽음으로 표시한다")
    void getEvents_읽음상태_반영() {
        User user = user(1L, "octocat");
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceMember membership = membership(user, ws);
        WorkspaceEvent unreadEvent = event(ws, WorkspaceEvent.EventType.ISSUE_CREATED, "alice", null);
        ReflectionTestUtils.setField(unreadEvent, "id", 10L);
        WorkspaceEvent readEvent = event(ws, WorkspaceEvent.EventType.REPLY, "bob", 1L);
        ReflectionTestUtils.setField(readEvent, "id", 11L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(membership));
        when(workspaceEventRepository.findDashboardEvents(
                eq(List.of(10L)), eq(1L),
                eq(List.of(WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED)),
                eq(List.of(WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION)),
                any(Pageable.class)
        )).thenReturn(List.of(unreadEvent, readEvent));
        when(workspaceEventReadStatusRepository.findReadEventIdsByUserIdAndEventIds(1L, List.of(10L, 11L)))
                .thenReturn(Set.of(11L));

        List<DashboardEventResponse> result = dashboardService.getEvents(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).eventId()).isEqualTo(10L);
        assertThat(result.get(0).isRead()).isFalse();
        assertThat(result.get(1).eventId()).isEqualTo(11L);
        assertThat(result.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("멤버십이 없으면 이벤트 조회를 수행하지 않고 빈 리스트를 반환한다")
    void getEvents_멤버십없으면_빈리스트() {
        User user = user(1L, "octocat");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of());

        List<DashboardEventResponse> result = dashboardService.getEvents(1L);

        assertThat(result).isEmpty();
        verify(workspaceEventRepository, never()).findDashboardEvents(any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    @DisplayName("사용자가 없으면 getEvents에서 NOT_FOUND 예외가 발생한다")
    void getEvents_유저없으면_NOT_FOUND() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.getEvents(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ── markEventAsRead ───────────────────────────────────────────────────

    @Test
    @DisplayName("targetUserId가 있고 본인이면 ReadStatus를 저장한다")
    void markEventAsRead_성공_targetUserId있고_본인이면() {
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceEvent ev = event(ws, WorkspaceEvent.EventType.REPLY, "alice", 1L);
        ReflectionTestUtils.setField(ev, "id", 200L);

        WorkspaceMember m = membership(user(1L, null), ws);
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findByIdWithWorkspace(200L)).thenReturn(Optional.of(ev));

        dashboardService.markEventAsRead(200L, 1L);

        verify(workspaceEventReadStatusRepository).save(any(WorkspaceEventReadStatus.class));
    }

    @Test
    @DisplayName("targetUserId가 없고 워크스페이스 멤버이면 ReadStatus를 저장한다")
    void markEventAsRead_성공_targetUserId없고_워크스페이스멤버면() {
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceEvent ev = event(ws, WorkspaceEvent.EventType.PR_CREATED, "alice", null);
        ReflectionTestUtils.setField(ev, "id", 200L);

        WorkspaceMember m = membership(user(1L, null), ws);
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findByIdWithWorkspace(200L)).thenReturn(Optional.of(ev));

        dashboardService.markEventAsRead(200L, 1L);

        verify(workspaceEventReadStatusRepository).save(any(WorkspaceEventReadStatus.class));
    }

    @Test
    @DisplayName("이벤트를 찾을 수 없으면 NOT_FOUND 예외가 발생한다")
    void markEventAsRead_이벤트없으면_NOT_FOUND() {
        WorkspaceMember m = membership(user(1L, null), workspace(10L, "워크스페이스"));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findByIdWithWorkspace(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.markEventAsRead(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("targetUserId가 다른 사용자이면 FORBIDDEN 예외가 발생한다")
    void markEventAsRead_권한없으면_FORBIDDEN_타깃유저_불일치() {
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceEvent ev = event(ws, WorkspaceEvent.EventType.REPLY, "alice", 99L);
        ReflectionTestUtils.setField(ev, "id", 200L);

        WorkspaceMember m = membership(user(1L, null), ws);
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findByIdWithWorkspace(200L)).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> dashboardService.markEventAsRead(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(ev.isRead()).isFalse();
    }

    @Test
    @DisplayName("targetUserId가 없는 broadcast 이벤트를 비소속 사용자가 읽으려 하면 FORBIDDEN 예외가 발생한다")
    void markEventAsRead_권한없으면_FORBIDDEN_비소속멤버() {
        Workspace ws = workspace(10L, "워크스페이스");
        WorkspaceEvent ev = event(ws, WorkspaceEvent.EventType.PR_CREATED, "alice", null);
        ReflectionTestUtils.setField(ev, "id", 200L);

        WorkspaceMember m = membership(user(1L, null), workspace(20L, "다른 워크스페이스"));
        when(workspaceMemberRepository.findAllByUser_IdAndIsActiveTrue(1L)).thenReturn(List.of(m));
        when(workspaceEventRepository.findByIdWithWorkspace(200L)).thenReturn(Optional.of(ev));

        assertThatThrownBy(() -> dashboardService.markEventAsRead(200L, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        assertThat(ev.isRead()).isFalse();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static User user(Long id, String githubUsername) {
        User user = User.create("user" + id + "@test.com", "hashed", "tester");
        ReflectionTestUtils.setField(user, "id", id);
        if (githubUsername != null) {
            ReflectionTestUtils.setField(user, "githubUsername", githubUsername);
        }
        return user;
    }

    private static Workspace workspace(Long id, String name) {
        User owner = User.create("owner@test.com", "hashed", "owner");
        Workspace workspace = Workspace.create(owner, name, "slug-" + id, null);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember membership(User user, Workspace workspace) {
        return WorkspaceMember.create(workspace, user, "editor");
    }

    private static WorkspaceEvent event(Workspace workspace, WorkspaceEvent.EventType type, String actorName, Long targetUserId) {
        return WorkspaceEvent.create(workspace, type, actorName, null, null, null, null, null, null, null, null, null, targetUserId);
    }
}
