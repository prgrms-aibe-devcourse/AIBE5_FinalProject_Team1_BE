package com.team1.codedock.domain.user.controller;

import com.team1.codedock.domain.user.service.GithubConnectService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GithubConnectControllerTest {

    private static final Long USER_ID = 100L;

    @Mock
    private GithubConnectService githubConnectService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new GithubConnectController(githubConnectService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("POST /connect/start - 인증 사용자 기준 GitHub authorize URL을 반환한다")
    void start_success() throws Exception {
        when(githubConnectService.buildAuthorizeUrl(USER_ID))
                .thenReturn("https://github.com/login/oauth/authorize?state=state-token");

        mockMvc.perform(post("/api/v1/users/me/github/connect/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.authorizeUrl").value(
                        "https://github.com/login/oauth/authorize?state=state-token"
                ));

        verify(githubConnectService).buildAuthorizeUrl(USER_ID);
    }

    @Test
    @DisplayName("POST /connect/start - GitHub 전용 계정이면 403을 반환한다")
    void start_githubOnlyUser() throws Exception {
        when(githubConnectService.buildAuthorizeUrl(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(post("/api/v1/users/me/github/connect/start"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"));

        verify(githubConnectService).buildAuthorizeUrl(USER_ID);
    }

    @Test
    @DisplayName("POST /connect/start - 인증 정보가 없으면 서비스를 호출하지 않고 401을 반환한다")
    void start_withoutAuthentication() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/api/v1/users/me/github/connect/start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C002"));

        verifyNoInteractions(githubConnectService);
    }

    @Test
    @DisplayName("GET /connect/callback - 서비스가 만든 callback URL로 redirect한다")
    void callback_redirectsToServiceResult() throws Exception {
        when(githubConnectService.completeConnect("code", "state-token"))
                .thenReturn("https://app.example.com/oauth/connect-callback?status=success");

        mockMvc.perform(get("/api/v1/users/me/github/connect/callback")
                        .param("code", "code")
                        .param("state", "state-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://app.example.com/oauth/connect-callback?status=success"))
                .andExpect(header().string("Location", "https://app.example.com/oauth/connect-callback?status=success"));

        verify(githubConnectService).completeConnect("code", "state-token");
    }

    @Test
    @DisplayName("GET /connect/callback - code가 없어도 서비스에 위임해 reason 있는 redirect를 반환한다")
    void callback_missingCode() throws Exception {
        when(githubConnectService.completeConnect(null, "state-token"))
                .thenReturn("https://app.example.com/oauth/connect-callback?status=error&reason=missing_code");

        mockMvc.perform(get("/api/v1/users/me/github/connect/callback")
                        .param("state", "state-token"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://app.example.com/oauth/connect-callback?status=error&reason=missing_code"));

        verify(githubConnectService).completeConnect(null, "state-token");
    }
}
