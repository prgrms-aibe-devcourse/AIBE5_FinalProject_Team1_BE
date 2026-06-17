package com.team1.codedock.domain.github.service;

import com.team1.codedock.domain.github.dto.GithubCollaboratorResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubApiServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RestClient.Builder restClientBuilder;

    @InjectMocks private GithubApiService githubApiService;

    private RestClient.Builder clonedBuilder;
    private RestClient restClient;
    @SuppressWarnings("rawtypes") private RestClient.RequestHeadersUriSpec uriSpec;
    @SuppressWarnings("rawtypes") private RestClient.RequestHeadersSpec headersSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        clonedBuilder = mock(RestClient.Builder.class);
        restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        headersSpec = mock(RestClient.RequestHeadersSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        lenient().when(restClientBuilder.clone()).thenReturn(clonedBuilder);
        lenient().when(clonedBuilder.baseUrl(anyString())).thenReturn(clonedBuilder);
        lenient().when(clonedBuilder.defaultHeader(anyString(), anyString())).thenReturn(clonedBuilder);
        lenient().when(clonedBuilder.build()).thenReturn(restClient);
        lenient().when(restClient.get()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(any(Function.class))).thenReturn(headersSpec);
        lenient().when(uriSpec.uri(anyString(), any(Object.class), any(Object.class))).thenReturn(headersSpec);
        lenient().when(headersSpec.retrieve()).thenReturn(responseSpec);
    }

    // ── getUserRepos ───────────────────────────────────────────

    @Test
    @DisplayName("유저를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생한다")
    void getUserRepos_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubApiService.getUserRepos(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("GitHub 토큰이 null이면 GITHUB_NOT_CONNECTED 예외가 발생한다")
    void getUserRepos_토큰_null이면_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubApiService.getUserRepos(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());
    }

    @Test
    @DisplayName("GitHub 토큰이 blank이면 GITHUB_NOT_CONNECTED 예외가 발생한다")
    void getUserRepos_토큰_blank이면_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("   ");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubApiService.getUserRepos(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());
    }

    @Test
    @DisplayName("레포 owner와 내 로그인이 일치하면 relation이 owner이다")
    @SuppressWarnings("unchecked")
    void getUserRepos_성공_owner_relation() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("octocat");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var ownerItem = new GithubApiService.GithubRepoApiItem.OwnerItem("octocat");
        var apiItem = new GithubApiService.GithubRepoApiItem(
                12345L, "hello-world", "octocat/hello-world", ownerItem,
                false, "Java", "https://github.com/octocat/hello-world", "main");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(apiItem));

        List<GithubRepoResponse> result = githubApiService.getUserRepos(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRelation()).isEqualTo("owner");
        assertThat(result.get(0).getName()).isEqualTo("hello-world");
    }

    @Test
    @DisplayName("레포 owner와 내 로그인이 다르면 relation이 collaborator이다")
    @SuppressWarnings("unchecked")
    void getUserRepos_성공_collaborator_relation() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("myuser");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var ownerItem = new GithubApiService.GithubRepoApiItem.OwnerItem("octocat");
        var apiItem = new GithubApiService.GithubRepoApiItem(
                12345L, "hello-world", "octocat/hello-world", ownerItem,
                false, "Java", "https://github.com/octocat/hello-world", "main");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(apiItem));

        List<GithubRepoResponse> result = githubApiService.getUserRepos(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRelation()).isEqualTo("collaborator");
    }

    @Test
    @DisplayName("API 응답이 null이면 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void getUserRepos_응답_null이면_빈리스트() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

        List<GithubRepoResponse> result = githubApiService.getUserRepos(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("HttpClientErrorException 발생 시 GITHUB_NOT_CONNECTED 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void getUserRepos_HttpClientError_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> githubApiService.getUserRepos(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());
    }

    @Test
    @DisplayName("Exception 발생 시 GITHUB_API_ERROR 예외가 발생한다")
    @SuppressWarnings("unchecked")
    void getUserRepos_Exception_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Connection error"));

        assertThatThrownBy(() -> githubApiService.getUserRepos(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_API_ERROR.getMessage());
    }

    // ── getRepoCollaborators ───────────────────────────────────

    @Test
    @DisplayName("유저를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생한다")
    void getRepoCollaborators_유저_없으면_예외() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("GitHub 토큰이 null이면 GITHUB_NOT_CONNECTED 예외가 발생한다")
    void getRepoCollaborators_토큰_null이면_예외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());
    }

    @Test
    @DisplayName("자기 자신은 협업자 목록에서 제외된다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_성공_자기자신_제외() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("octocat");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var self = new GithubApiService.GithubCollaboratorApiItem(
                "octocat", "https://avatars.githubusercontent.com/u/1", "https://github.com/octocat");
        var other = new GithubApiService.GithubCollaboratorApiItem(
                "collaborator1", "https://avatars.githubusercontent.com/u/2", "https://github.com/collaborator1");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(self, other));
        when(userRepository.findByGithubUsernameIn(any())).thenReturn(List.of());

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLogin()).isEqualTo("collaborator1");
    }

    @Test
    @DisplayName("플랫폼 등록 유저이고 displayName이 있으면 displayName을 반환한다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_성공_displayName_있음() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("me");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var apiItem = new GithubApiService.GithubCollaboratorApiItem(
                "collaborator1", "https://avatars.githubusercontent.com/u/2", "https://github.com/collaborator1");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(apiItem));

        User platformUser = mock(User.class);
        when(platformUser.getId()).thenReturn(2L);
        when(platformUser.getEmail()).thenReturn("collab@example.com");
        when(platformUser.getDisplayName()).thenReturn("Collaborator One");
        when(platformUser.getGithubUsername()).thenReturn("collaborator1");
        when(userRepository.findByGithubUsernameIn(any())).thenReturn(List.of(platformUser));

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isEqualTo(2L);
        assertThat(result.get(0).getEmail()).isEqualTo("collab@example.com");
        assertThat(result.get(0).getDisplayName()).isEqualTo("Collaborator One");
    }

    @Test
    @DisplayName("플랫폼 등록 유저이고 displayName이 null이면 username을 반환한다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_성공_displayName_없으면_username_반환() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("me");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var apiItem = new GithubApiService.GithubCollaboratorApiItem(
                "collaborator1", "https://avatars.githubusercontent.com/u/2", "https://github.com/collaborator1");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(apiItem));

        User platformUser = mock(User.class);
        when(platformUser.getId()).thenReturn(2L);
        when(platformUser.getEmail()).thenReturn("collab@example.com");
        when(platformUser.getDisplayName()).thenReturn(null);
        when(platformUser.getUsername()).thenReturn("collaborator1username");
        when(platformUser.getGithubUsername()).thenReturn("collaborator1");
        when(userRepository.findByGithubUsernameIn(any())).thenReturn(List.of(platformUser));

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDisplayName()).isEqualTo("collaborator1username");
    }

    @Test
    @DisplayName("플랫폼 미등록 유저이면 userId, email, displayName이 null이다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_성공_플랫폼유저_없음() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(user.getGithubUsername()).thenReturn("me");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        var apiItem = new GithubApiService.GithubCollaboratorApiItem(
                "collaborator1", "https://avatars.githubusercontent.com/u/2", "https://github.com/collaborator1");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(apiItem));
        when(userRepository.findByGithubUsernameIn(any())).thenReturn(List.of());

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUserId()).isNull();
        assertThat(result.get(0).getEmail()).isNull();
        assertThat(result.get(0).getDisplayName()).isNull();
    }

    @Test
    @DisplayName("API 응답이 null이면 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_응답_null이면_빈리스트() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("HttpClientErrorException 발생 시 예외 없이 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_HttpClientError_빈리스트() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null));

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Exception 발생 시 예외 없이 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void getRepoCollaborators_Exception_빈리스트() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("token");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenThrow(new RuntimeException("Connection error"));

        List<GithubCollaboratorResponse> result = githubApiService.getRepoCollaborators(1L, "octocat", "hello-world");

        assertThat(result).isEmpty();
    }

    // ── getRepo ───────────────────────────────────────────────

    @Test
    @DisplayName("레포지토리 정보를 정상적으로 조회하고 반환한다")
    void getRepo_성공() {
        var ownerItem = new GithubApiService.GithubRepoApiItem.OwnerItem("octocat");
        var apiItem = new GithubApiService.GithubRepoApiItem(
                12345L, "hello-world", "octocat/hello-world", ownerItem,
                false, "Java", "https://github.com/octocat/hello-world", "main");
        when(responseSpec.body(GithubApiService.GithubRepoApiItem.class)).thenReturn(apiItem);

        GithubRepoResponse result = githubApiService.getRepo("octocat", "hello-world", "token");

        assertThat(result.getName()).isEqualTo("hello-world");
        assertThat(result.getOwner()).isEqualTo("octocat");
        assertThat(result.getDefaultBranch()).isEqualTo("main");
    }

    @Test
    @DisplayName("레포지토리가 없으면 GITHUB_REPO_NOT_FOUND 예외가 발생한다")
    void getRepo_NotFound_예외() {
        when(responseSpec.body(GithubApiService.GithubRepoApiItem.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> githubApiService.getRepo("octocat", "hello-world", "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("HttpClientErrorException 발생 시 GITHUB_NOT_CONNECTED 예외가 발생한다")
    void getRepo_HttpClientError_예외() {
        when(responseSpec.body(GithubApiService.GithubRepoApiItem.class))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> githubApiService.getRepo("octocat", "hello-world", "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_NOT_CONNECTED.getMessage());
    }

    @Test
    @DisplayName("Exception 발생 시 GITHUB_API_ERROR 예외가 발생한다")
    void getRepo_Exception_예외() {
        when(responseSpec.body(GithubApiService.GithubRepoApiItem.class))
                .thenThrow(new RuntimeException("Connection error"));

        assertThatThrownBy(() -> githubApiService.getRepo("octocat", "hello-world", "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_API_ERROR.getMessage());
    }

    @Test
    @DisplayName("API 응답이 null이면 GITHUB_REPO_NOT_FOUND 예외가 발생한다")
    void getRepo_응답_null이면_예외() {
        when(responseSpec.body(GithubApiService.GithubRepoApiItem.class)).thenReturn(null);

        assertThatThrownBy(() -> githubApiService.getRepo("octocat", "hello-world", "token"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());
    }
}
