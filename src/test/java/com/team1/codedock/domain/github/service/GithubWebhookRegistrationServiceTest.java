package com.team1.codedock.domain.github.service;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubWebhookRegistrationServiceTest {

    @Mock
    private GithubRepositoryRepository githubRepositoryRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestClient.Builder restClientBuilder;

    @InjectMocks
    private GithubWebhookRegistrationService githubWebhookRegistrationService;

    @Test
    @DisplayName("userId가 null이면 UNAUTHORIZED 예외를 던진다")
    void registerWebhook_userId_null이면_UNAUTHORIZED() {
        assertThatThrownBy(() -> githubWebhookRegistrationService.registerWebhook(1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("owner/admin이 아닌 권한은 FORBIDDEN 예외를 던진다")
    void registerWebhook_권한없는_멤버는_FORBIDDEN() {
        WorkspaceMember viewer = mock(WorkspaceMember.class);
        when(viewer.getAuthority()).thenReturn("viewer");
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 10L))
                .thenReturn(Optional.of(viewer));

        assertThatThrownBy(() -> githubWebhookRegistrationService.registerWebhook(1L, 1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("GitHub 토큰이 없으면 GITHUB_NOT_CONNECTED 예외를 던진다")
    void registerWebhook_github_토큰없으면_GITHUB_NOT_CONNECTED() {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(1L);
        GithubRepository repo = mock(GithubRepository.class);
        when(repo.getWorkspace()).thenReturn(workspace);

        WorkspaceMember admin = mock(WorkspaceMember.class);
        when(admin.getAuthority()).thenReturn("admin");
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn(null);

        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 10L))
                .thenReturn(Optional.of(admin));
        when(githubRepositoryRepository.findById(1L)).thenReturn(Optional.of(repo));
        when(userRepository.findById(10L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubWebhookRegistrationService.registerWebhook(1L, 1L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GITHUB_NOT_CONNECTED);
    }
}
