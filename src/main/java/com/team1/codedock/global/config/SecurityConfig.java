package com.team1.codedock.global.config;

import com.team1.codedock.domain.auth.handler.OAuth2FailureHandler;
import com.team1.codedock.domain.auth.handler.OAuth2SuccessHandler;
import com.team1.codedock.domain.auth.service.CustomOAuth2UserService;
import com.team1.codedock.global.security.CookieOAuth2AuthorizationRequestRepository;
import com.team1.codedock.global.security.CustomUserDetailsService;
import com.team1.codedock.global.security.JwtAuthFilter;
import com.team1.codedock.global.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final List<String> LOCAL_NETWORK_ORIGIN_PATTERNS = List.of(
            "http://10.*:*",
            "http://172.16.*:*",
            "http://172.17.*:*",
            "http://172.18.*:*",
            "http://172.19.*:*",
            "http://172.20.*:*",
            "http://172.21.*:*",
            "http://172.22.*:*",
            "http://172.23.*:*",
            "http://172.24.*:*",
            "http://172.25.*:*",
            "http://172.26.*:*",
            "http://172.27.*:*",
            "http://172.28.*:*",
            "http://172.29.*:*",
            "http://172.30.*:*",
            "http://172.31.*:*",
            "http://192.168.*:*",
            "http://*.local:*",
            "https://10.*:*",
            "https://172.16.*:*",
            "https://172.17.*:*",
            "https://172.18.*:*",
            "https://172.19.*:*",
            "https://172.20.*:*",
            "https://172.21.*:*",
            "https://172.22.*:*",
            "https://172.23.*:*",
            "https://172.24.*:*",
            "https://172.25.*:*",
            "https://172.26.*:*",
            "https://172.27.*:*",
            "https://172.28.*:*",
            "https://172.29.*:*",
            "https://172.30.*:*",
            "https://172.31.*:*",
            "https://192.168.*:*",
            "https://*.local:*"
    );

    private final CustomOAuth2UserService oAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;
    private final CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;
    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Value("${app.cors.allowed-origin-patterns:${app.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*,http://[::1]:*}}")
    private String[] allowedOriginPatterns;

    @Value("${app.local-network-origin-enabled:false}")
    private boolean localNetworkOriginEnabled;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/api-docs/**",
                                "/actuator/health",
                                "/ws/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/github/connect/callback").permitAll()
                        // GitHub Webhook — HMAC 서명으로 인증하므로 JWT 불필요
                        .requestMatchers(HttpMethod.POST, "/api/v1/github/webhooks/**").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .authorizationRequestRepository(cookieAuthorizationRequestRepository))
                        .redirectionEndpoint(redirect -> redirect
                                .baseUri("/login/oauth2/code/*"))
                        .userInfoEndpoint(userInfo ->
                                userInfo.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .addFilterBefore(jwtAuthFilter(), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(401);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"success\":false,\"code\":\"C002\",\"message\":\"인증이 필요합니다.\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(403);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"success\":false,\"code\":\"C003\",\"message\":\"접근 권한이 없습니다.\"}");
                        })
                );
        return http.build();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter() {
        return new JwtAuthFilter(jwtProvider, userDetailsService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(resolveAllowedOriginPatterns());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private List<String> resolveAllowedOriginPatterns() {
        java.util.stream.Stream<String> configuredPatterns =
                Arrays.stream(allowedOriginPatterns == null ? new String[0] : allowedOriginPatterns);
        java.util.stream.Stream<String> localNetworkPatterns = localNetworkOriginEnabled
                ? LOCAL_NETWORK_ORIGIN_PATTERNS.stream()
                : java.util.stream.Stream.empty();

        return java.util.stream.Stream.concat(configuredPatterns, localNetworkPatterns)
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .distinct()
                .toList();
    }
}
