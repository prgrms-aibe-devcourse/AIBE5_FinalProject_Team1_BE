package com.team1.codedock.domain.document.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.document.dto.SwaggerSyncResponse;
import com.team1.codedock.domain.document.entity.ApiSpec;
import com.team1.codedock.domain.document.repository.ApiSpecRepository;
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
import org.mockito.Mock;
import org.mockito.Spy;
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
class ApiSpecSwaggerServiceTest {

    @Mock private ApiSpecRepository apiSpecRepository;
    @Mock private WorkspaceRepository workspaceRepository;
    @Mock private WorkspaceMemberRepository workspaceMemberRepository;
    @Mock private RestClient.Builder restClientBuilder;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    private ApiSpecSwaggerService apiSpecSwaggerService;

    @Mock private RestClient restClient;
    @SuppressWarnings("rawtypes") @Mock private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;
    @SuppressWarnings("rawtypes") @Mock private RestClient.RequestHeadersSpec requestHeadersSpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private static final String SWAGGER_URL = "http://localhost:8080/v3/api-docs";
    private static final String VALID_SWAGGER_JSON =
            "{\"paths\":{\"/api/users\":{\"get\":{\"operationId\":\"getUsers\",\"summary\":\"사용자 조회\",\"tags\":[\"User\"]}}}}";

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

        apiSpecSwaggerService = new ApiSpecSwaggerService(
                apiSpecRepository, workspaceRepository, workspaceMemberRepository,
                restClientBuilder, objectMapper
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Workspace mockWorkspace(String swaggerUrl) {
        Workspace workspace = mock(Workspace.class);
        lenient().when(workspace.getId()).thenReturn(1L);
        lenient().when(workspace.getSwaggerUrl()).thenReturn(swaggerUrl);
        return workspace;
    }

    // ── registerAndSync() ─────────────────────────────────────

    @Test
    @DisplayName("Swagger URL을 등록하고 파싱된 API 명세를 저장한다")
    void registerAndSync_성공() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(VALID_SWAGGER_JSON);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of());

        SwaggerSyncResponse response = apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL);

        assertThat(response.swaggerUrl()).isEqualTo(SWAGGER_URL);
        assertThat(response.syncedCount()).isEqualTo(1);
        verify(workspace).updateSwaggerUrl(SWAGGER_URL);
        verify(apiSpecRepository).deleteAllByWorkspace_IdAndSourceType(1L, "swagger");
        verify(apiSpecRepository).saveAll(any());
    }

    @Test
    @DisplayName("워크스페이스가 없으면 예외가 발생한다")
    void registerAndSync_워크스페이스_없으면_예외() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("멤버가 없으면 예외가 발생한다")
    void registerAndSync_멤버_없으면_예외() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    // ── resync() ──────────────────────────────────────────────

    @Test
    @DisplayName("기존 Swagger URL로 재동기화한다")
    void resync_성공() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(VALID_SWAGGER_JSON);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of());

        SwaggerSyncResponse response = apiSpecSwaggerService.resync(1L);

        assertThat(response.syncedCount()).isEqualTo(1);
        verify(apiSpecRepository).deleteAllByWorkspace_IdAndSourceType(1L, "swagger");
        verify(apiSpecRepository).saveAll(any());
    }

    @Test
    @DisplayName("Swagger URL 등록 시 데이터를 가져오는 데 실패하면 예외가 발생한다")
    void registerAndSync_fetch_실패_예외() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_FETCH_ERROR.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("재동기화 시 멤버가 없으면 예외가 발생한다")
    void resync_멤버_없으면_예외() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecSwaggerService.resync(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Swagger URL이 등록되지 않은 상태에서 재동기화하면 예외가 발생한다")
    void resync_URL_미등록_예외() {
        Workspace workspace = mockWorkspace(null);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> apiSpecSwaggerService.resync(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_URL_NOT_REGISTERED.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("재동기화 시 워크스페이스가 없으면 예외가 발생한다")
    void resync_워크스페이스_없으면_예외() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecSwaggerService.resync(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Swagger URL에서 데이터를 가져오는 데 실패하면 예외가 발생한다")
    void resync_swagger_fetch_실패_예외() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> apiSpecSwaggerService.resync(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_FETCH_ERROR.getMessage());

        verify(apiSpecRepository, never()).saveAll(any());
    }

    // ── getSwaggerUrl() ───────────────────────────────────────

    @Test
    @DisplayName("등록된 Swagger URL을 반환한다")
    void getSwaggerUrl_성공() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        String result = apiSpecSwaggerService.getSwaggerUrl(1L);

        assertThat(result).isEqualTo(SWAGGER_URL);
    }

    @Test
    @DisplayName("Swagger URL이 없으면 예외가 발생한다")
    void getSwaggerUrl_URL_없으면_예외() {
        Workspace workspace = mockWorkspace(null);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));

        assertThatThrownBy(() -> apiSpecSwaggerService.getSwaggerUrl(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.SWAGGER_URL_NOT_REGISTERED.getMessage());
    }

    @Test
    @DisplayName("getSwaggerUrl 시 워크스페이스가 없으면 예외가 발생한다")
    void getSwaggerUrl_워크스페이스_없으면_예외() {
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> apiSpecSwaggerService.getSwaggerUrl(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());
    }

    // ── fetchAndParse() 메서드 필터링 ────────────────────────────

    @Test
    @DisplayName("Swagger JSON에 유효하지 않은 HTTP 메서드가 있으면 해당 경로는 저장하지 않는다")
    void fetchAndParse_유효하지_않은_method_필터링() {
        String jsonWithInvalidMethod =
                "{\"paths\":{\"/api/users\":{\"head\":{\"operationId\":\"headUsers\",\"summary\":\"헤드 요청\"}}}}";

        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(jsonWithInvalidMethod);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of());

        SwaggerSyncResponse response = apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL);

        assertThat(response.syncedCount()).isEqualTo(0);
    }

    // ── doSync() 체크리스트 자동완료 ───────────────────────────

    @Test
    @DisplayName("Swagger 엔드포인트와 일치하는 미완료 체크리스트를 자동으로 완료 처리한다")
    void doSync_매칭되는_체크리스트_자동완료() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpec aiSpec = ApiSpec.createFromAi(workspace, member, "매칭 API", "GET", "/api/users", "User", null, null);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(VALID_SWAGGER_JSON);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of(aiSpec));

        SwaggerSyncResponse response = apiSpecSwaggerService.resync(1L);

        assertThat(aiSpec.getStatus()).isEqualTo("completed");
        assertThat(response.completedChecklistCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 completed인 체크리스트는 완료 카운트에서 제외된다")
    void doSync_이미_완료된_체크리스트_카운트_제외() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpec completedSpec = ApiSpec.createFromAi(workspace, member, "완료된 API", "GET", "/api/users", null, null, null);
        completedSpec.complete();

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(VALID_SWAGGER_JSON);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of(completedSpec));

        SwaggerSyncResponse response = apiSpecSwaggerService.resync(1L);

        assertThat(response.completedChecklistCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("엔드포인트가 일치하지 않는 체크리스트는 상태를 유지한다")
    void doSync_매칭_안되는_체크리스트_상태_유지() {
        Workspace workspace = mockWorkspace(SWAGGER_URL);
        WorkspaceMember member = mock(WorkspaceMember.class);

        ApiSpec nonMatchingSpec = ApiSpec.createFromAi(workspace, member, "미매칭 API", "POST", "/api/orders", null, null, null);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findByWorkspace_IdAndUser_IdAndIsActiveTrue(1L, 1L)).thenReturn(Optional.of(member));
        when(responseSpec.body(String.class)).thenReturn(VALID_SWAGGER_JSON);
        when(apiSpecRepository.saveAll(any())).thenReturn(List.of());
        when(apiSpecRepository.findAllByWorkspace_IdAndSourceTypeNot(1L, "swagger")).thenReturn(List.of(nonMatchingSpec));

        SwaggerSyncResponse response = apiSpecSwaggerService.resync(1L);

        assertThat(nonMatchingSpec.getStatus()).isEqualTo("design");
        assertThat(response.completedChecklistCount()).isEqualTo(0);
    }
}
