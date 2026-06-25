package com.team1.codedock.domain.user.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.GithubConnectTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubConnectServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GithubConnectClient githubConnectClient;

    @Mock
    private GithubConnectTokenProvider connectTokenProvider;

    private GithubConnectService githubConnectService;

    @BeforeEach
    void setUp() {
        githubConnectService = new GithubConnectService(
                userRepository,
                githubConnectClient,
                connectTokenProvider
        );
        ReflectionTestUtils.setField(githubConnectService, "clientId", "client-id");
        ReflectionTestUtils.setField(
                githubConnectService,
                "connectRedirectUri",
                "https://api.example.com/api/v1/users/me/github/connect/callback"
        );
        ReflectionTestUtils.setField(
                githubConnectService,
                "connectCallbackUri",
                "https://app.example.com/oauth/connect-callback"
        );
    }

    @Test
    @DisplayName("buildAuthorizeUrl - 이메일 계정의 GitHub 재연결 URL에 scope와 state를 포함한다")
    void buildAuthorizeUrl_success() {
        User user = emailUser(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(connectTokenProvider.generate(1L)).thenReturn("state-token");

        String result = githubConnectService.buildAuthorizeUrl(1L);

        assertThat(result).startsWith("https://github.com/login/oauth/authorize?");
        assertThat(result).contains("client_id=client-id");
        assertThat(result).contains("redirect_uri=https://api.example.com/api/v1/users/me/github/connect/callback");
        assertThat(result).contains("scope=read:user user:email repo read:project");
        assertThat(result).contains("state=state-token");
    }

    @Test
    @DisplayName("buildAuthorizeUrl - GitHub 전용 계정은 재연결 시작을 거부한다")
    void buildAuthorizeUrl_githubOnlyUser() {
        User user = User.createFromGithub("github-1", "octocat", "octocat@example.com", null, "token");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubConnectService.buildAuthorizeUrl(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
        verifyNoInteractions(connectTokenProvider);
    }

    @Test
    @DisplayName("buildAuthorizeUrl - 비활성 계정은 재연결 URL을 만들 수 없다")
    void buildAuthorizeUrl_inactiveUser() {
        User user = emailUser(1L);
        user.deactivateAccount("deleted-user-1@codedock.local", "deleted-user-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubConnectService.buildAuthorizeUrl(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
        verifyNoInteractions(connectTokenProvider);
    }

    @Test
    @DisplayName("completeConnect - 정상 callback이면 사용자에 GitHub 정보를 다시 연결한다")
    void completeConnect_success() {
        User user = emailUser(1L);
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubConnectClient.exchangeCodeForAccessToken("code")).thenReturn("github-token");
        when(githubConnectClient.fetchGithubIdentity("github-token"))
                .thenReturn(new GithubConnectClient.GithubIdentity("github-1", "octocat", "octocat@example.com"));
        when(userRepository.findByGithubId("github-1")).thenReturn(Optional.empty());

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo("https://app.example.com/oauth/connect-callback?status=success");
        assertThat(user.isGithubConnected()).isTrue();
        assertThat(user.getGithubId()).isEqualTo("github-1");
        assertThat(user.getGithubUsername()).isEqualTo("octocat");
        assertThat(user.getGithubAccessToken()).isEqualTo("github-token");
    }

    @Test
    @DisplayName("completeConnect - 이미 다른 사용자가 연결한 GitHub 계정이면 conflict reason을 반환한다")
    void completeConnect_conflict() {
        User user = emailUser(1L);
        User owner = emailUser(2L);
        owner.linkGithub("github-1", "octocat", "octocat@example.com", null, "owner-token");
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubConnectClient.exchangeCodeForAccessToken("code")).thenReturn("github-token");
        when(githubConnectClient.fetchGithubIdentity("github-token"))
                .thenReturn(new GithubConnectClient.GithubIdentity("github-1", "octocat", "octocat@example.com"));
        when(userRepository.findByGithubId("github-1")).thenReturn(Optional.of(owner));

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=conflict&reason=github_already_connected"
        );
        assertThat(user.isGithubConnected()).isFalse();
    }

    @Test
    @DisplayName("completeConnect - state가 잘못되면 invalid_state reason을 반환하고 GitHub API를 호출하지 않는다")
    void completeConnect_invalidState() {
        when(connectTokenProvider.validateAndGetUserId("bad-state"))
                .thenThrow(new IllegalArgumentException("bad state"));

        String result = githubConnectService.completeConnect("code", "bad-state");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=invalid_state"
        );
        verifyNoInteractions(userRepository, githubConnectClient);
    }

    @Test
    @DisplayName("completeConnect - code가 없으면 missing_code reason을 반환한다")
    void completeConnect_missingCode() {
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);

        String result = githubConnectService.completeConnect(null, "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=missing_code"
        );
        verifyNoInteractions(userRepository, githubConnectClient);
    }

    @Test
    @DisplayName("completeConnect - GitHub token 교환 실패 시 token_exchange_failed reason을 반환한다")
    void completeConnect_tokenExchangeFailed() {
        User user = emailUser(1L);
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubConnectClient.exchangeCodeForAccessToken("code")).thenReturn(null);

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=token_exchange_failed"
        );
    }

    @Test
    @DisplayName("completeConnect - GitHub identity 조회 실패 시 identity_fetch_failed reason을 반환한다")
    void completeConnect_identityFetchFailed() {
        User user = emailUser(1L);
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(githubConnectClient.exchangeCodeForAccessToken("code")).thenReturn("github-token");
        when(githubConnectClient.fetchGithubIdentity("github-token")).thenReturn(null);

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=identity_fetch_failed"
        );
    }

    @Test
    @DisplayName("completeConnect - callback 사용자가 비활성 상태면 invalid_user reason을 반환한다")
    void completeConnect_inactiveUser() {
        User user = emailUser(1L);
        user.deactivateAccount("deleted-user-1@codedock.local", "deleted-user-1");
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=invalid_user"
        );
        verifyNoInteractions(githubConnectClient);
    }

    @Test
    @DisplayName("completeConnect - GitHub 전용 계정이면 email_account_required reason을 반환한다")
    void completeConnect_githubOnlyUser() {
        User user = User.createFromGithub("github-1", "octocat", "octocat@example.com", null, "token");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(connectTokenProvider.validateAndGetUserId("state-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        String result = githubConnectService.completeConnect("code", "state-token");

        assertThat(result).isEqualTo(
                "https://app.example.com/oauth/connect-callback?status=error&reason=email_account_required"
        );
        verifyNoInteractions(githubConnectClient);
    }

    private static User emailUser(Long id) {
        User user = User.create("user" + id + "@example.com", "hashed-password", "사용자" + id);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
