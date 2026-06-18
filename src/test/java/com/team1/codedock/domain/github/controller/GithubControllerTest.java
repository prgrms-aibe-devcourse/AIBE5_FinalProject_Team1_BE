package com.team1.codedock.domain.github.controller;

import com.team1.codedock.domain.github.dto.GithubCollaboratorResponse;
import com.team1.codedock.domain.github.dto.GithubRepoResponse;
import com.team1.codedock.domain.github.service.GithubApiService;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubControllerTest {

    @Mock
    private GithubApiService githubApiService;

    @InjectMocks
    private GithubController githubController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(githubController)
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

    private GithubRepoResponse sampleRepo() {
        return GithubRepoResponse.builder()
                .id(12345L)
                .name("hello-world")
                .fullName("octocat/hello-world")
                .owner("octocat")
                .isPrivate(false)
                .language("Java")
                .htmlUrl("https://github.com/octocat/hello-world")
                .defaultBranch("main")
                .relation("owner")
                .build();
    }

    private GithubCollaboratorResponse sampleCollaborator() {
        return GithubCollaboratorResponse.builder()
                .login("collaborator1")
                .avatarUrl("https://avatars.githubusercontent.com/u/1")
                .htmlUrl("https://github.com/collaborator1")
                .userId(2L)
                .email("collaborator1@example.com")
                .displayName("Collaborator One")
                .build();
    }

    // ── GET /api/v1/github/repos ───────────────────────────────

    @Test
    @DisplayName("내 레포지토리 목록 조회 성공 시 200과 레포 목록을 반환한다")
    void getMyRepos_성공_200() throws Exception {
        when(githubApiService.getUserRepos(1L)).thenReturn(List.of(sampleRepo()));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("hello-world"))
                .andExpect(jsonPath("$.data[0].relation").value("owner"));
    }

    @Test
    @DisplayName("레포지토리가 없으면 200과 빈 목록을 반환한다")
    void getMyRepos_빈_목록_200() throws Exception {
        when(githubApiService.getUserRepos(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 404를 반환한다")
    void getMyRepos_유저_없으면_404() throws Exception {
        when(githubApiService.getUserRepos(1L))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("GitHub 계정이 연결되지 않았으면 400을 반환한다")
    void getMyRepos_GitHub_연결_안됨_400() throws Exception {
        when(githubApiService.getUserRepos(1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G005"));
    }

    @Test
    @DisplayName("GitHub API 오류가 발생하면 502를 반환한다")
    void getMyRepos_GitHub_API_오류_502() throws Exception {
        when(githubApiService.getUserRepos(1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_API_ERROR));

        mockMvc.perform(get("/api/v1/github/repos"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G006"));
    }

    // ── GET /api/v1/github/repos/{owner}/{repo}/collaborators ──

    @Test
    @DisplayName("협업자 목록 조회 성공 시 200과 협업자 목록을 반환한다")
    void getCollaborators_성공_200() throws Exception {
        when(githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .thenReturn(List.of(sampleCollaborator()));

        mockMvc.perform(get("/api/v1/github/repos/octocat/hello-world/collaborators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].login").value("collaborator1"))
                .andExpect(jsonPath("$.data[0].displayName").value("Collaborator One"));
    }

    @Test
    @DisplayName("GitHub API 오류 시 200과 빈 목록을 반환한다")
    void getCollaborators_빈_목록_200() throws Exception {
        when(githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/github/repos/octocat/hello-world/collaborators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    @DisplayName("유저를 찾을 수 없으면 404를 반환한다")
    void getCollaborators_유저_없으면_404() throws Exception {
        when(githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/github/repos/octocat/hello-world/collaborators"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("GitHub 계정이 연결되지 않았으면 400을 반환한다")
    void getCollaborators_GitHub_연결_안됨_400() throws Exception {
        when(githubApiService.getRepoCollaborators(1L, "octocat", "hello-world"))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_NOT_CONNECTED));

        mockMvc.perform(get("/api/v1/github/repos/octocat/hello-world/collaborators"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G005"));
    }
}
