package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.github.dto.GithubConnectRequest;
import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
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
class GithubRepositoryServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiService githubApiService;

    @InjectMocks
    private GithubRepositoryService githubRepositoryService;

    private GithubConnectRequest request() {
        GithubConnectRequest req = new GithubConnectRequest();
        req.setOwner("octocat");
        req.setRepo("hello-world");
        return req;
    }

    private User mockUser() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("github-token");
        return user;
    }

    private WorkspaceMember mockMember() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        when(member.getWorkspace()).thenReturn(workspace);
        return member;
    }

    private GithubRepoResponse mockRepoInfo() {
        return GithubRepoResponse.builder()
                .id(12345L)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .htmlUrl("https://github.com/octocat/hello-world")
                .isPrivate(false)
                .defaultBranch("main")
                .build();
    }

    private GithubRepository mockSavedRepo() {
        GithubRepository saved = mock(GithubRepository.class);
        when(saved.getId()).thenReturn(1L);
        when(saved.getOwner()).thenReturn("octocat");
        when(saved.getName()).thenReturn("hello-world");
        when(saved.getFullName()).thenReturn("octocat/hello-world");
        when(saved.getUrl()).thenReturn("https://github.com/octocat/hello-world");
        when(saved.getDefaultBranch()).thenReturn("main");
        when(saved.isPrivate()).thenReturn(false);
        return saved;
    }

    // ── connectRepository() ───────────────────────────────────

    @Test
    @DisplayName("정상적으로 GitHub 레포지토리를 워크스페이스에 연결하고 응답을 반환한다")
    void connectRepository_성공() {
        User user = mockUser();
        WorkspaceMember member = mockMember();

        GithubRepository savedRepo = mockSavedRepo();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());
        when(githubApiService.getRepo("octocat", "hello-world", "github-token")).thenReturn(mockRepoInfo());
        when(githubRepositoryRepository.save(any(GithubRepository.class))).thenReturn(savedRepo);

        GithubConnectResponse response = githubRepositoryService.connectRepository(1L, 1L, request());

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getOwner()).isEqualTo("octocat");
        assertThat(response.getName()).isEqualTo("hello-world");
        assertThat(response.getFullName()).isEqualTo("octocat/hello-world");
        assertThat(response.getDefaultBranch()).isEqualTo("main");
        verify(githubRepositoryRepository).save(any(GithubRepository.class));
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 예외가 발생한다")
    void connectRepository_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(githubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("워크스페이스 멤버를 찾을 수 없으면 예외가 발생한다")
    void connectRepository_멤버_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(githubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 연결된 레포지토리가 있으면 예외가 발생한다")
    void connectRepository_이미_연결된_레포_있으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(mock(GithubRepository.class)));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_ALREADY_CONNECTED.getMessage());

        verify(githubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 토큰이 없으면 예외가 발생한다")
    void connectRepository_GitHub_토큰_없으면_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());

        verify(githubRepositoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("GitHub 레포지토리를 찾을 수 없으면 예외가 발생한다")
    void connectRepository_GitHub_레포_없으면_예외() {
        User user = mockUser();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());
        when(githubApiService.getRepo(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        assertThatThrownBy(() -> githubRepositoryService.connectRepository(1L, 1L, request()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());

        verify(githubRepositoryRepository, never()).save(any());
    }
}
