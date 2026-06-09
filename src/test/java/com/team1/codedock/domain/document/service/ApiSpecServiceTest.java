package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.document.dto.ApiSpecCreateRequest;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.dto.ApiSpecUpdateRequest;
import com.team1.codedock.domain.document.entity.ApiSpec;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
import com.team1.codedock.domain.issue.repository.GithubIssueRepository;
import com.team1.codedock.domain.pr.repository.GithubPullRequestRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSpecServiceTest {

    @Mock
    private ApiSpecRepository apiSpecRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private GithubIssueRepository githubIssueRepository;

    @Mock
    private GithubPullRequestRepository githubPullRequestRepository;

    @InjectMocks
    private ApiSpecService apiSpecService;

    private ApiSpecCreateRequest minimalCreateRequest() {
        return new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );
    }

    private ApiSpec sampleSpec() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        return ApiSpec.create(
                workspace, member,
                "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );
    }

    // ── createApiSpec() ───────────────────────────────────────

    @Test
    @DisplayName("필수 필드만으로 API 명세를 정상 생성한다")
    void createApiSpec_성공() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(apiSpecRepository.save(any(ApiSpec.class))).thenReturn(sampleSpec());

        ApiSpecResponse response = apiSpecService.createApiSpec(1L, minimalCreateRequest());

        assertThat(response.title()).isEqualTo("회원 조회");
        assertThat(response.method()).isEqualTo("GET");
        verify(apiSpecRepository).save(any(ApiSpec.class));
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스로 생성 시 예외가 발생한다")
    void createApiSpec_워크스페이스_없으면_예외() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.createApiSpec(1L, minimalCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 멤버로 생성 시 예외가 발생한다")
    void createApiSpec_멤버_없으면_예외() {
        Workspace workspace = mock(Workspace.class);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.createApiSpec(1L, minimalCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).save(any());
    }

    @Test
    @DisplayName("assigneeId가 있으면 assignee를 조회하여 생성한다")
    void createApiSpec_assigneeId_있으면_assignee_조회() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        WorkspaceMember assignee = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, 2L,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(2L, 1L)).thenReturn(Optional.of(assignee));
        when(apiSpecRepository.save(any(ApiSpec.class))).thenReturn(sampleSpec());

        apiSpecService.createApiSpec(1L, request);

        verify(workspaceMemberRepository).findByIdAndWorkspace_Id(2L, 1L);
        verify(apiSpecRepository).save(any(ApiSpec.class));
    }

    @Test
    @DisplayName("relatedPrId가 있으면 PR을 워크스페이스 조건으로 조회한다")
    void createApiSpec_relatedPrId_있으면_workspace_조건_조회() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, 10L, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(10L, 1L))
                .thenReturn(Optional.of(mock(com.team1.codedock.domain.pr.entity.GithubPullRequest.class)));
        when(apiSpecRepository.save(any(ApiSpec.class))).thenReturn(sampleSpec());

        apiSpecService.createApiSpec(1L, request);

        verify(githubPullRequestRepository).findByIdAndRepository_Workspace_Id(10L, 1L);
    }

    @Test
    @DisplayName("relatedIssueId가 있으면 Issue를 워크스페이스 조건으로 조회한다")
    void createApiSpec_relatedIssueId_있으면_workspace_조건_조회() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, 20L, null, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(20L, 1L))
                .thenReturn(Optional.of(mock(com.team1.codedock.domain.issue.entity.GithubIssue.class)));
        when(apiSpecRepository.save(any(ApiSpec.class))).thenReturn(sampleSpec());

        apiSpecService.createApiSpec(1L, request);

        verify(githubIssueRepository).findByIdAndRepository_Workspace_Id(20L, 1L);
    }

    @Test
    @DisplayName("다른 워크스페이스의 PR을 연결하면 예외가 발생한다")
    void createApiSpec_다른_워크스페이스_PR_예외() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, 10L, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.createApiSpec(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_PR_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 워크스페이스의 Issue를 연결하면 예외가 발생한다")
    void createApiSpec_다른_워크스페이스_Issue_예외() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, 20L, null, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.createApiSpec(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_ISSUE_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).save(any());
    }

    @Test
    @DisplayName("assigneeId가 존재하지 않는 멤버이면 예외가 발생한다")
    void createApiSpec_assigneeId_없는_멤버_예외() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, 99L,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(member));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.createApiSpec(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).save(any());
    }

    // ── getApiSpecs() ─────────────────────────────────────────

    @Test
    @DisplayName("필터 없이 전체 API 명세 목록을 반환한다")
    void getApiSpecs_전체_조회() {
        when(apiSpecRepository.findAllByWorkspace_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(sampleSpec()));

        List<ApiSpecResponse> result = apiSpecService.getApiSpecs(1L, null, null);

        assertThat(result).hasSize(1);
        verify(apiSpecRepository).findAllByWorkspace_IdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("groupName 필터로 조회한다")
    void getApiSpecs_groupName_필터() {
        when(apiSpecRepository.findAllByWorkspace_IdAndGroupNameOrderByCreatedAtDesc(1L, "User"))
                .thenReturn(List.of(sampleSpec()));

        List<ApiSpecResponse> result = apiSpecService.getApiSpecs(1L, "User", null);

        assertThat(result).hasSize(1);
        verify(apiSpecRepository).findAllByWorkspace_IdAndGroupNameOrderByCreatedAtDesc(1L, "User");
        verify(apiSpecRepository, never()).findAllByWorkspace_IdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("status 필터로 조회한다")
    void getApiSpecs_status_필터() {
        when(apiSpecRepository.findAllByWorkspace_IdAndStatusOrderByCreatedAtDesc(1L, "design"))
                .thenReturn(List.of(sampleSpec()));

        List<ApiSpecResponse> result = apiSpecService.getApiSpecs(1L, null, "design");

        assertThat(result).hasSize(1);
        verify(apiSpecRepository).findAllByWorkspace_IdAndStatusOrderByCreatedAtDesc(1L, "design");
        verify(apiSpecRepository, never()).findAllByWorkspace_IdOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("API 명세가 없으면 빈 리스트를 반환한다")
    void getApiSpecs_빈_리스트() {
        when(apiSpecRepository.findAllByWorkspace_IdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        List<ApiSpecResponse> result = apiSpecService.getApiSpecs(1L, null, null);

        assertThat(result).isEmpty();
    }

    // ── getApiSpec() ──────────────────────────────────────────

    @Test
    @DisplayName("API 명세 단건을 정상적으로 조회한다")
    void getApiSpec_성공() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L))
                .thenReturn(Optional.of(sampleSpec()));

        ApiSpecResponse response = apiSpecService.getApiSpec(1L, 1L);

        assertThat(response.title()).isEqualTo("회원 조회");
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 조회 시 예외가 발생한다")
    void getApiSpec_없으면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.getApiSpec(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("다른 워크스페이스의 API 명세를 조회하면 예외가 발생한다")
    void getApiSpec_다른_워크스페이스면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.getApiSpec(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }

    // ── updateApiSpec() ───────────────────────────────────────

    @Test
    @DisplayName("API 명세를 정상적으로 수정한다")
    void updateApiSpec_성공() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L))
                .thenReturn(Optional.of(spec));

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, "completed", null,
                null, null, null, null, null, 201,
                null, null, null, null, null
        );

        ApiSpecResponse response = apiSpecService.updateApiSpec(1L, 1L, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.method()).isEqualTo("POST");
        assertThat(response.status()).isEqualTo("completed");
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 수정 시 예외가 발생한다")
    void updateApiSpec_없으면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(99L, 1L))
                .thenReturn(Optional.empty());

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThatThrownBy(() -> apiSpecService.updateApiSpec(1L, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("다른 워크스페이스의 API 명세를 수정하면 예외가 발생한다")
    void updateApiSpec_다른_워크스페이스면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 999L))
                .thenReturn(Optional.empty());

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThatThrownBy(() -> apiSpecService.updateApiSpec(999L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("수정 시 assigneeId가 있으면 assignee를 조회하여 수정한다")
    void updateApiSpec_assigneeId_있으면_assignee_조회() {
        ApiSpec spec = sampleSpec();
        WorkspaceMember assignee = mock(WorkspaceMember.class);
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(spec));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(2L, 1L)).thenReturn(Optional.of(assignee));

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, 2L,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        apiSpecService.updateApiSpec(1L, 1L, request);

        verify(workspaceMemberRepository).findByIdAndWorkspace_Id(2L, 1L);
    }

    @Test
    @DisplayName("수정 시 relatedPrId가 있으면 PR을 워크스페이스 조건으로 조회한다")
    void updateApiSpec_relatedPrId_있으면_workspace_조건_조회() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(spec));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(10L, 1L))
                .thenReturn(Optional.of(mock(com.team1.codedock.domain.pr.entity.GithubPullRequest.class)));

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, 10L, null
        );

        apiSpecService.updateApiSpec(1L, 1L, request);

        verify(githubPullRequestRepository).findByIdAndRepository_Workspace_Id(10L, 1L);
    }

    @Test
    @DisplayName("수정 시 relatedIssueId가 있으면 Issue를 워크스페이스 조건으로 조회한다")
    void updateApiSpec_relatedIssueId_있으면_workspace_조건_조회() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(spec));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(20L, 1L))
                .thenReturn(Optional.of(mock(com.team1.codedock.domain.issue.entity.GithubIssue.class)));

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, 20L, null, null
        );

        apiSpecService.updateApiSpec(1L, 1L, request);

        verify(githubIssueRepository).findByIdAndRepository_Workspace_Id(20L, 1L);
    }

    @Test
    @DisplayName("수정 시 다른 워크스페이스의 PR을 연결하면 예외가 발생한다")
    void updateApiSpec_다른_워크스페이스_PR_예외() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(spec));
        when(githubPullRequestRepository.findByIdAndRepository_Workspace_Id(10L, 1L)).thenReturn(Optional.empty());

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, 10L, null
        );

        assertThatThrownBy(() -> apiSpecService.updateApiSpec(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_PR_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("수정 시 다른 워크스페이스의 Issue를 연결하면 예외가 발생한다")
    void updateApiSpec_다른_워크스페이스_Issue_예외() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L)).thenReturn(Optional.of(spec));
        when(githubIssueRepository.findByIdAndRepository_Workspace_Id(20L, 1L)).thenReturn(Optional.empty());

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, 20L, null, null
        );

        assertThatThrownBy(() -> apiSpecService.updateApiSpec(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_ISSUE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("수정 시 assigneeId가 존재하지 않는 멤버이면 예외가 발생한다")
    void updateApiSpec_assigneeId_없는_멤버_예외() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L))
                .thenReturn(Optional.of(spec));
        when(workspaceMemberRepository.findByIdAndWorkspace_Id(99L, 1L)).thenReturn(Optional.empty());

        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, 99L,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThatThrownBy(() -> apiSpecService.updateApiSpec(1L, 1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());
    }

    // ── deleteApiSpec() ───────────────────────────────────────

    @Test
    @DisplayName("API 명세를 정상적으로 삭제한다")
    void deleteApiSpec_성공() {
        ApiSpec spec = sampleSpec();
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 1L))
                .thenReturn(Optional.of(spec));

        apiSpecService.deleteApiSpec(1L, 1L);

        verify(apiSpecRepository).delete(spec);
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 삭제 시 예외가 발생한다")
    void deleteApiSpec_없으면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.deleteApiSpec(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("다른 워크스페이스의 API 명세를 삭제하면 예외가 발생한다")
    void deleteApiSpec_다른_워크스페이스면_예외() {
        when(apiSpecRepository.findByIdAndWorkspace_Id(1L, 999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecService.deleteApiSpec(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.API_SPEC_NOT_FOUND.getMessage());
    }
}
