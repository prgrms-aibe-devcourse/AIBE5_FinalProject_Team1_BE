package com.team1.codedock.global.security;

import com.team1.codedock.domain.user.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private FilterChain filterChain;

    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        jwtAuthFilter = new JwtAuthFilter(jwtProvider, userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("보호 API에서 만료된 access token이면 TOKEN_EXPIRED 401을 반환하고 체인을 중단한다")
    void doFilterWithExpiredAccessTokenOnProtectedApiReturnsTokenExpired() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/auth/me");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateAccessTokenWithResult("expired-token"))
                .thenReturn(JwtValidationResult.EXPIRED);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getContentAsString()).contains("\"code\":\"TOKEN_EXPIRED\"");
        assertThat(response.getContentAsString()).contains("Access token이 만료되었습니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    @DisplayName("보호 API에서 무효 access token이면 INVALID_TOKEN 401을 반환하고 체인을 중단한다")
    void doFilterWithInvalidAccessTokenOnProtectedApiReturnsInvalidToken() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/workspaces");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateAccessTokenWithResult("bad-token"))
                .thenReturn(JwtValidationResult.INVALID);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"INVALID_TOKEN\"");
        assertThat(response.getContentAsString()).contains("유효하지 않은 토큰입니다.");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain, never()).doFilter(any(ServletRequest.class), any(ServletResponse.class));
    }

    @Test
    @DisplayName("토큰이 없으면 인증 처리를 하지 않고 뒤 필터에 인증 필요 판단을 맡긴다")
    void doFilterWithoutTokenContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/workspaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(jwtProvider, userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("정상 access token이면 SecurityContext에 인증 사용자를 저장하고 체인을 계속 진행한다")
    void doFilterWithValidAccessTokenStoresAuthentication() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/workspaces");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CustomUserDetails userDetails = userDetails(1L);
        when(jwtProvider.validateAccessTokenWithResult("valid-token"))
                .thenReturn(JwtValidationResult.VALID);
        when(jwtProvider.getUserId("valid-token")).thenReturn(1L);
        when(userDetailsService.loadUserById(1L)).thenReturn(userDetails);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isSameAs(userDetails);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("공개 auth API에서는 만료 access token 헤더가 함께 와도 refresh 요청을 막지 않는다")
    void doFilterWithExpiredAccessTokenOnPublicAuthApiContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/refresh");
        request.addHeader("Authorization", "Bearer expired-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateAccessTokenWithResult("expired-token"))
                .thenReturn(JwtValidationResult.EXPIRED);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("공개 WebSocket handshake 경로에서는 access token 검증 실패를 HTTP 필터에서 막지 않는다")
    void doFilterWithInvalidAccessTokenOnWebSocketPathContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = request("GET", "/ws/info");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateAccessTokenWithResult("bad-token"))
                .thenReturn(JwtValidationResult.INVALID);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("Prometheus actuator 경로에서는 access token 검증 실패를 HTTP 필터에서 막지 않는다")
    void doFilterWithInvalidAccessTokenOnPrometheusPathContinuesFilterChain() throws Exception {
        MockHttpServletRequest request = request("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateAccessTokenWithResult("bad-token"))
                .thenReturn(JwtValidationResult.INVALID);

        jwtAuthFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private CustomUserDetails userDetails(Long userId) {
        User user = User.create("user@test.com", "password", "tester");
        ReflectionTestUtils.setField(user, "id", userId);
        return new CustomUserDetails(user);
    }
}
