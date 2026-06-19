package com.team1.codedock.global.config;

import com.team1.codedock.domain.auth.handler.OAuth2FailureHandler;
import com.team1.codedock.domain.auth.handler.OAuth2SuccessHandler;
import com.team1.codedock.domain.auth.service.CustomOAuth2UserService;
import com.team1.codedock.global.security.CookieOAuth2AuthorizationRequestRepository;
import com.team1.codedock.global.security.CustomUserDetailsService;
import com.team1.codedock.global.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private CustomOAuth2UserService oAuth2UserService;

    @Mock
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @Mock
    private OAuth2FailureHandler oAuth2FailureHandler;

    @Mock
    private CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private CustomUserDetailsService userDetailsService;

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                oAuth2UserService,
                oAuth2SuccessHandler,
                oAuth2FailureHandler,
                cookieAuthorizationRequestRepository,
                jwtProvider,
                userDetailsService
        );
    }

    @Test
    @DisplayName("CORS는 LAN IP 프론트 origin pattern을 허용한다")
    void corsConfigurationAllowsLanFrontendOriginPattern() {
        setAllowedOriginPatterns("http://localhost:*", "http://192.168.*:*");

        CorsConfiguration cors = corsConfiguration("/api/channels/1/messages");

        assertThat(cors.getAllowedOriginPatterns())
                .containsExactly(
                        "http://localhost:*",
                        "http://192.168.*:*",
                        "http://127.0.0.1:*",
                        "http://[::1]:*",
                        "http://10.*:*",
                        "http://172.*:*",
                        "http://*.local:*",
                        "https://10.*:*",
                        "https://172.*:*",
                        "https://192.168.*:*",
                        "https://*.local:*"
                );
        assertThat(cors.checkOrigin("http://192.168.0.164:5173"))
                .isEqualTo("http://192.168.0.164:5173");
        assertThat(cors.checkOrigin("http://localhost:5173"))
                .isEqualTo("http://localhost:5173");
        assertThat(cors.getAllowCredentials()).isTrue();
        assertThat(cors.checkHttpMethod(HttpMethod.GET)).contains(HttpMethod.GET);
        assertThat(cors.checkHttpMethod(HttpMethod.OPTIONS)).contains(HttpMethod.OPTIONS);
        assertThat(cors.checkHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)))
                .containsExactly(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE);
    }

    @Test
    @DisplayName("CORS는 설정되지 않은 외부 origin을 허용하지 않는다")
    void corsConfigurationRejectsUnconfiguredPublicOrigin() {
        setAllowedOriginPatterns("http://localhost:*", "http://192.168.*:*");

        CorsConfiguration cors = corsConfiguration("/api/channels/1/messages");

        assertThat(cors.checkOrigin("http://203.0.113.10:5173")).isNull();
        assertThat(cors.checkOrigin("https://192.168.0.164:5173"))
                .isEqualTo("https://192.168.0.164:5173");
    }

    @Test
    @DisplayName("CORS origin pattern 설정의 null, blank, 중복 값을 제거한다")
    void corsConfigurationCleansBlankAndDuplicateOriginPatterns() {
        setAllowedOriginPatterns(null, "", " ", "http://localhost:*", "http://localhost:*", "http://127.0.0.1:*");

        CorsConfiguration cors = corsConfiguration("/api/channels/1/messages");

        assertThat(cors.getAllowedOriginPatterns())
                .containsExactly(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://[::1]:*",
                        "http://10.*:*",
                        "http://172.*:*",
                        "http://192.168.*:*",
                        "http://*.local:*",
                        "https://10.*:*",
                        "https://172.*:*",
                        "https://192.168.*:*",
                        "https://*.local:*"
                );
    }

    @Test
    @DisplayName("CORS 설정은 모든 API path에 동일하게 적용된다")
    void corsConfigurationAppliesToEveryPath() {
        setAllowedOriginPatterns("http://192.168.*:*");

        CorsConfiguration apiCors = corsConfiguration("/api/channels/1/messages");
        CorsConfiguration wsCors = corsConfiguration("/ws/info");

        assertThat(apiCors).isNotNull();
        assertThat(wsCors).isNotNull();
        assertThat(apiCors.checkOrigin("http://192.168.0.164:5173"))
                .isEqualTo("http://192.168.0.164:5173");
        assertThat(wsCors.checkOrigin("http://192.168.0.164:5173"))
                .isEqualTo("http://192.168.0.164:5173");
    }

    private void setAllowedOriginPatterns(String... patterns) {
        ReflectionTestUtils.setField(securityConfig, "allowedOriginPatterns", patterns);
    }

    private CorsConfiguration corsConfiguration(String path) {
        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest(HttpMethod.OPTIONS.name(), path);
        request.addHeader(HttpHeaders.ORIGIN, "http://192.168.0.164:5173");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
        return source.getCorsConfiguration(request);
    }
}
