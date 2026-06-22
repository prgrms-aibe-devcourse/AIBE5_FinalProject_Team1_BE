package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.ai.service.GeminiClient;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.github.repository.GithubRepositoryRepository;
import com.team1.codedock.domain.github.service.GithubApiClient;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiSpecAiServiceTest {

    @Mock private ApiSpecRepository apiSpecRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private UserRepository userRepository;
    @Mock private GithubRepositoryRepository githubRepositoryRepository;
    @Mock private GithubApiClient githubApiClient;
    @Mock private GeminiClient geminiClient;
    @Mock private RestClient.Builder restClientBuilder;

    @InjectMocks
    private ApiSpecAiService apiSpecAiService;

    @Mock private RestClient restClient;
    @SuppressWarnings("rawtypes") @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes") @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(1L);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);

        lenient().when(restClientBuilder.build()).thenReturn(restClient);
        lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Workspace mockWorkspace() {
        Workspace workspace = mock(Workspace.class);
        lenient().when(workspace.getId()).thenReturn(1L);
        lenient().when(workspace.getSwaggerUrl()).thenReturn("http://localhost:8080/v3/api-docs");
        return workspace;
    }

    private User mockUser() {
        User user = mock(User.class);
        when(user.getGithubAccessToken()).thenReturn("github-token");
        return user;
    }

    private GithubRepository mockGithubRepo() {
        GithubRepository repo = mock(GithubRepository.class);
        when(repo.getOwner()).thenReturn("owner");
        when(repo.getName()).thenReturn("repo");
        when(repo.getDefaultBranch()).thenReturn("main");
        return repo;
    }

    // ── generateChecklist() ───────────────────────────────────

    @Test
    @DisplayName("Gemini AI 체크리스트를 정상적으로 생성하고 저장한다")
    void generateChecklist_성공() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistItem item = new GeminiClient.ApiSpecChecklistItem(
                "누락 API", "GET", "/api/items", "Item", "아이템 조회", "상세 설명");
        GeminiClient.ApiSpecChecklistResult result = new GeminiClient.ApiSpecChecklistResult(List.of(item));

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(result);
        when(apiSpecRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).method()).isEqualTo("GET");
        assertThat(responses.get(0).sourceType()).isEqualTo("AI");
        verify(apiSpecRepository).saveAll(any());
    }

    @Test
    @DisplayName("repoSources가 비어 있어도 예외 없이 Swagger만으로 체크리스트를 생성한다")
    void generateChecklist_repoSources_비어있어도_정상_동작() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistItem item = new GeminiClient.ApiSpecChecklistItem(
                "누락 API", "POST", "/api/items", "Item", "아이템 생성", null);
        GeminiClient.ApiSpecChecklistResult result = new GeminiClient.ApiSpecChecklistResult(List.of(item));

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of());
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(result);
        when(apiSpecRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).method()).isEqualTo("POST");
    }

    @Test
    @DisplayName("워크스페이스가 없으면 예외가 발생한다")
    void generateChecklist_워크스페이스_없으면_예외() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Swagger URL이 등록되지 않았으면 예외가 발생한다")
    void generateChecklist_swagger_URL_없으면_예외() {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getSwaggerUrl()).thenReturn(null);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_URL_NOT_REGISTERED.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 예외가 발생한다")
    void generateChecklist_유저_없으면_예외() {
        Workspace workspace = mockWorkspace();
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("워크스페이스 멤버가 없으면 예외가 발생한다")
    void generateChecklist_멤버_없으면_예외() {
        Workspace workspace = mockWorkspace();
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("GitHub 레포지토리를 찾을 수 없으면 예외가 발생한다")
    void generateChecklist_GitHub_레포_없으면_예외() {
        Workspace workspace = mockWorkspace();
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(mock(WorkspaceMember.class)));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.GITHUB_REPO_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Gemini가 null을 반환하면 빈 리스트를 반환하고 저장하지 않는다")
    void generateChecklist_Gemini_null_반환_빈_리스트() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(null);

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).isEmpty();
        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Gemini가 checklist가 null인 결과를 반환하면 빈 리스트를 반환하고 저장하지 않는다")
    void generateChecklist_Gemini_checklist_null_빈_리스트() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistResult nullChecklistResult = new GeminiClient.ApiSpecChecklistResult(null);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(nullChecklistResult);

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).isEmpty();
        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Gemini 체크리스트가 비어 있으면 빈 리스트를 반환하고 저장하지 않는다")
    void generateChecklist_Gemini_결과_비어있으면_빈_리스트() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistResult emptyResult = new GeminiClient.ApiSpecChecklistResult(List.of());

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(emptyResult);

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).isEmpty();
        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Swagger URL에서 데이터를 가져오는 데 실패하면 예외가 발생한다")
    void generateChecklist_swagger_fetch_실패_예외() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> apiSpecAiService.generateChecklist(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_FETCH_ERROR.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("title, method, endpoint 중 하나라도 null인 항목은 제외한다")
    void generateChecklist_필수_필드_null_항목_필터링() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistItem noTitle = new GeminiClient.ApiSpecChecklistItem(
                null, "GET", "/api/items", null, null, null);
        GeminiClient.ApiSpecChecklistItem noMethod = new GeminiClient.ApiSpecChecklistItem(
                "API", null, "/api/items", null, null, null);
        GeminiClient.ApiSpecChecklistItem noEndpoint = new GeminiClient.ApiSpecChecklistItem(
                "API", "GET", null, null, null, null);
        GeminiClient.ApiSpecChecklistResult result = new GeminiClient.ApiSpecChecklistResult(
                List.of(noTitle, noMethod, noEndpoint));

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(result);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Gemini가 유효하지 않은 HTTP 메서드를 반환하면 해당 항목을 제외한다")
    void generateChecklist_유효하지_않은_method_필터링() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistItem invalidItem = new GeminiClient.ApiSpecChecklistItem(
                "잘못된 API", "INVALID", "/api/bad", null, null, null);
        GeminiClient.ApiSpecChecklistResult result = new GeminiClient.ApiSpecChecklistResult(List.of(invalidItem));

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(result);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).isEmpty();
    }

    @Test
    @DisplayName("Gemini가 소문자 메서드를 반환하면 대문자로 정규화하여 저장한다")
    void generateChecklist_method_소문자_정규화() {
        Workspace workspace = mockWorkspace();
        User user = mockUser();
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubRepository githubRepo = mockGithubRepo();
        GeminiClient.ApiSpecChecklistItem lowercaseItem = new GeminiClient.ApiSpecChecklistItem(
                "소문자 메서드 API", "get", "/api/items", "Item", "아이템 조회", null);
        GeminiClient.ApiSpecChecklistResult result = new GeminiClient.ApiSpecChecklistResult(List.of(lowercaseItem));

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(githubRepositoryRepository.findByWorkspaceId(1L)).thenReturn(List.of(githubRepo));
        when(githubApiClient.fetchRepoSources(any(), any(), any(), any())).thenReturn(List.of("@Entity public class Item {}"));
        when(responseSpec.body(String.class)).thenReturn("{\"paths\":{}}");
        when(geminiClient.generateApiSpecChecklist(any(), any())).thenReturn(result);
        when(apiSpecRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<ApiSpecResponse> responses = apiSpecAiService.generateChecklist(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).method()).isEqualTo("GET");
    }
}
