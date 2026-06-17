package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.github.dto.GithubConnectResponse;
import com.team1.codedock.domain.github.service.GithubRepositoryService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceGithubControllerTest {

    @Mock
    private GithubRepositoryService githubRepositoryService;

    @InjectMocks
    private WorkspaceGithubController workspaceGithubController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(workspaceGithubController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();

        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(1L);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private GithubConnectResponse sampleConnectResponse() {
        return GithubConnectResponse.builder()
                .id(1L)
                .owner("octocat")
                .name("hello-world")
                .fullName("octocat/hello-world")
                .url("https://github.com/octocat/hello-world")
                .defaultBranch("main")
                .isPrivate(false)
                .build();
    }

    // ── POST /api/v1/workspaces/{workspaceId}/github ───────────

    @Test
    @DisplayName("GitHub 레포지토리 연결 성공 시 200과 연결 정보를 반환한다")
    void connectRepository_성공_200() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any())).thenReturn(sampleConnectResponse());

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.owner").value("octocat"))
                .andExpect(jsonPath("$.data.name").value("hello-world"));
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 404를 반환한다")
    void connectRepository_유저_없으면_404() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("워크스페이스 멤버를 찾을 수 없으면 404를 반환한다")
    void connectRepository_멤버_없으면_404() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("W002"));
    }

    @Test
    @DisplayName("이미 연결된 레포지토리가 있으면 409를 반환한다")
    void connectRepository_이미_연결된_레포_있으면_409() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_ALREADY_CONNECTED));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G007"));
    }

    @Test
    @DisplayName("GitHub 계정이 연결되지 않았으면 400을 반환한다")
    void connectRepository_GitHub_연결_안됨_400() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G005"));
    }

    @Test
    @DisplayName("GitHub API 오류가 발생하면 502를 반환한다")
    void connectRepository_GitHub_API_오류_502() throws Exception {
        when(githubRepositoryService.connectRepository(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_API_ERROR));

        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"octocat\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G006"));
    }

    @Test
    @DisplayName("owner가 없으면 400을 반환한다")
    void connectRepository_요청_바디_없으면_400() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/1/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"owner\":\"\",\"repo\":\"hello-world\"}"))
                .andExpect(status().isBadRequest());
    }
}
