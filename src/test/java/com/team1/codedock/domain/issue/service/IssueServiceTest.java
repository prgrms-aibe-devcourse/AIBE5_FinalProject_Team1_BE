package com.team1.codedock.domain.issue.service;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.issue.dto.IssueLocalStatusUpdateRequest;
import com.team1.codedock.domain.issue.dto.IssueResponse;
import com.team1.codedock.domain.issue.entity.GithubIssue;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.issue.repository.IssueAssigneeRepository;
import com.team1.codedock.domain.issue.repository.IssueLabelRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock private GithubIssueRepository githubIssueRepository;
    @Mock private IssueLabelRepository issueLabelRepository;
    @Mock private IssueAssigneeRepository issueAssigneeRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService(
                githubIssueRepository,
                issueLabelRepository,
                issueAssigneeRepository,
                workspaceMemberRepository
        );
    }

    @Test
    @DisplayName("열려 있는 이슈는 작업보드 상태를 todo에서 done으로 변경할 수 있다")
    void updateLocalStatus_openIssue_updatesToDone() {
        GithubIssue issue = issue("open");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(40L, 10L)).thenReturn(Optional.of(issue));
        when(issueLabelRepository.findAllByGithubIssue_Id(40L)).thenReturn(List.of());
        when(issueAssigneeRepository.findAllByGithubIssue_Id(40L)).thenReturn(List.of());

        IssueResponse response =
                issueService.updateLocalStatus(10L, 40L, 100L, new IssueLocalStatusUpdateRequest("done"));

        assertThat(issue.getLocalStatus()).isEqualTo("done");
        assertThat(response.state()).isEqualTo("open");
        assertThat(response.localStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("GitHub에서 닫힌 이슈는 작업보드에서 done 상태를 유지할 수 있다")
    void updateLocalStatus_closedIssue_allowsDone() {
        GithubIssue issue = issue("closed");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(40L, 10L)).thenReturn(Optional.of(issue));
        when(issueLabelRepository.findAllByGithubIssue_Id(40L)).thenReturn(List.of());
        when(issueAssigneeRepository.findAllByGithubIssue_Id(40L)).thenReturn(List.of());

        IssueResponse response =
                issueService.updateLocalStatus(10L, 40L, 100L, new IssueLocalStatusUpdateRequest("done"));

        assertThat(issue.getState()).isEqualTo("closed");
        assertThat(issue.getLocalStatus()).isEqualTo("done");
        assertThat(response.localStatus()).isEqualTo("done");
    }

    @Test
    @DisplayName("GitHub에서 닫힌 이슈를 todo로 되돌리면 INVALID_INPUT 예외가 발생한다")
    void updateLocalStatus_closedIssue_rejectsTodo() {
        GithubIssue issue = issue("closed");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(40L, 10L)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() ->
                issueService.updateLocalStatus(10L, 40L, 100L, new IssueLocalStatusUpdateRequest("todo")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        assertThat(issue.getLocalStatus()).isEqualTo("done");
        verifyNoInteractions(issueLabelRepository, issueAssigneeRepository);
    }

    @Test
    @DisplayName("GitHub에서 닫힌 이슈를 in_progress로 되돌리면 상태가 변경되지 않는다")
    void updateLocalStatus_closedIssue_keepsDoneWhenRejected() {
        GithubIssue issue = issue("closed");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(40L, 10L)).thenReturn(Optional.of(issue));

        assertThatThrownBy(() ->
                issueService.updateLocalStatus(10L, 40L, 100L, new IssueLocalStatusUpdateRequest("in_progress")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("GitHub에서 닫힌 이슈는 완료 상태로만 유지할 수 있습니다.");

        assertThat(issue.getLocalStatus()).isEqualTo("done");
        verify(issueLabelRepository, never()).findAllByGithubIssue_Id(40L);
        verify(issueAssigneeRepository, never()).findAllByGithubIssue_Id(40L);
    }

    @Test
    @DisplayName("인증 사용자 id가 없으면 작업보드 상태 변경을 거부한다")
    void updateLocalStatus_nullUser_unauthorized() {
        assertThatThrownBy(() ->
                issueService.updateLocalStatus(10L, 40L, null, new IssueLocalStatusUpdateRequest("done")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(githubIssueRepository, issueLabelRepository, issueAssigneeRepository);
    }

    @Test
    @DisplayName("워크스페이스 이슈가 아니면 작업보드 상태 변경을 거부한다")
    void updateLocalStatus_issueNotFound() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(40L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                issueService.updateLocalStatus(10L, 40L, 100L, new IssueLocalStatusUpdateRequest("done")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_ISSUE_NOT_FOUND);

        verifyNoInteractions(issueLabelRepository, issueAssigneeRepository);
    }

    @Test
    @DisplayName("작업보드 목록 조회는 닫힌 이슈를 done 상태로 반환한다")
    void getWorkspaceIssues_returnsClosedIssueAsDone() {
        GithubIssue openIssue = issue("open");
        GithubIssue closedIssue = issue("closed");
        ReflectionTestUtils.setField(closedIssue, "id", 41L);
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.of(WorkspaceMember.create(workspace(), User.create("member@example.com", "pw", "멤버"), "viewer")));
        when(githubIssueRepository.findAllByWorkspaceId(10L)).thenReturn(List.of(openIssue, closedIssue));
        when(issueLabelRepository.findAllByGithubIssue_IdIn(List.of(40L, 41L))).thenReturn(List.of());
        when(issueAssigneeRepository.findAllByIssueIdInFetchUser(List.of(40L, 41L))).thenReturn(List.of());

        List<IssueResponse> responses = issueService.getWorkspaceIssues(10L, 100L);

        assertThat(responses)
                .extracting(IssueResponse::state, IssueResponse::localStatus)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("open", "todo"),
                        org.assertj.core.groups.Tuple.tuple("closed", "done")
                );
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 작업보드 목록 조회를 거부한다")
    void getWorkspaceIssues_forbidden() {
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(10L, 100L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueService.getWorkspaceIssues(10L, 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);

        verifyNoInteractions(githubIssueRepository, issueLabelRepository, issueAssigneeRepository);
    }

    @Test
    @DisplayName("인증 사용자 id가 없으면 작업보드 목록 조회를 거부한다")
    void getWorkspaceIssues_nullUser_unauthorized() {
        assertThatThrownBy(() -> issueService.getWorkspaceIssues(10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(githubIssueRepository, issueLabelRepository, issueAssigneeRepository);
    }

    private static GithubIssue issue(String state) {
        Workspace workspace = workspace();
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
        ReflectionTestUtils.setField(repository, "id", 30L);
        Channel channel = Channel.createRepository(workspace, repository, "repo");
        ReflectionTestUtils.setField(channel, "id", 20L);

        GithubIssue issue = GithubIssue.create(
                repository,
                channel,
                "9001",
                1,
                "이슈",
                "설명",
                state,
                "https://github.com/team/repo/issues/1",
                "octocat",
                "[]",
                "closed".equals(state) ? LocalDateTime.of(2026, 6, 25, 12, 0) : null,
                LocalDateTime.of(2026, 6, 24, 10, 0),
                LocalDateTime.of(2026, 6, 25, 11, 0)
        );
        ReflectionTestUtils.setField(issue, "id", 40L);
        return issue;
    }

    private static Workspace workspace() {
        Workspace workspace = Workspace.create(User.create("owner@example.com", "pw", "오너"), "팀", "team", null);
        ReflectionTestUtils.setField(workspace, "id", 10L);
        return workspace;
    }
}
