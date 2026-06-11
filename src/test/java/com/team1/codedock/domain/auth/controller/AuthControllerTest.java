package com.team1.codedock.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.auth.dto.*;
import com.team1.codedock.domain.auth.service.AuthService;
import com.team1.codedock.domain.user.dto.UserResponse;
import com.team1.codedock.domain.user.entity.User;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── POST /api/v1/auth/signup ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /signup: 200 응답과 회원 정보를 반환한다")
    void signup_success() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setEmail("test@test.com");
        req.setDisplayName("testuser");
        req.setPassword("password1");
        req.setGithubLinkToken("link-token");

        SignupResponse response = SignupResponse.builder()
                .userId(1L).email("test@test.com").username("testuser").build();
        when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("POST /signup: 이메일이 blank이면 400을 반환한다")
    void signup_blankEmail_400() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setEmail("");
        req.setDisplayName("testuser");
        req.setPassword("password1");
        req.setGithubLinkToken("link-token");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("POST /signup: 이메일 형식이 잘못되면 400을 반환한다")
    void signup_invalidEmailFormat_400() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setEmail("not-an-email");
        req.setDisplayName("testuser");
        req.setPassword("password1");
        req.setGithubLinkToken("link-token");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }
    
    @Test
    @DisplayName("POST /signup: githubLinkToken이 blank이면 400을 반환한다")
    void signup_blankGithubLinkToken_400() throws Exception {
        SignupRequest req = new SignupRequest();
        req.setEmail("test@test.com");
        req.setDisplayName("testuser");
        req.setPassword("password1");
        req.setGithubLinkToken("");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /login: 200 응답과 access/refresh 토큰을 반환한다")
    void login_success() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("test@test.com");
        req.setPassword("password1");

        LoginResponse response = LoginResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .user(LoginUserInfo.builder()
                        .id(1L).email("test@test.com").username("testuser").build())
                .build();
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.data.user.email").value("test@test.com"));
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /refresh: 200 응답과 새로운 토큰 쌍을 반환한다")
    void refresh_success() throws Exception {
        TokenResponse response = new TokenResponse("new-access-token", "new-refresh-token");
        when(authService.refresh(anyString())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("old-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    @DisplayName("POST /refresh: refreshToken이 blank이면 400을 반환한다")
    void refresh_blankToken_400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout: 200 응답을 반환한다")
    void logout_success() throws Exception {
        doNothing().when(authService).logout(anyString());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest("some-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(authService).logout("some-token");
    }

    // ── GET /api/v1/auth/me ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /me: 인증된 유저의 정보를 반환한다")
    void me_success() throws Exception {
        setAuthentication(1L);

        UserResponse response = UserResponse.builder()
                .id(1L).email("test@test.com").username("testuser").build();
        when(authService.me(1L)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.email").value("test@test.com"))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    @DisplayName("GET /me: 인증 정보 없으면 401을 반환한다")
    void me_unauthenticated_401() throws Exception {
        // SecurityContext is empty — no setAuthentication() called

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C002"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void setAuthentication(Long userId) {
        User user = User.create("test@test.com", "hashed", "testuser");
        ReflectionTestUtils.setField(user, "id", userId);
        CustomUserDetails userDetails = new CustomUserDetails(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities())
        );
    }
}