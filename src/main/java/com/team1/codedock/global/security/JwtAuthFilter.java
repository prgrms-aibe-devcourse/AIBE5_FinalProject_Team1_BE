package com.team1.codedock.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            JwtValidationResult validationResult = jwtProvider.validateAccessTokenWithResult(token);
            if (validationResult == JwtValidationResult.VALID) {
                Long userId = jwtProvider.getUserId(token);
                CustomUserDetails userDetails;
                try {
                    userDetails = userDetailsService.loadUserById(userId);
                } catch (RuntimeException e) {
                    SecurityErrorResponseWriter.write(response, ErrorCode.INVALID_TOKEN);
                    return;
                }

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else if (!isPublicEndpoint(request)) {
                // 보호 API에서는 토큰 실패 원인을 명확히 내려 프론트가 refresh/login 분기를 할 수 있게 함.
                SecurityErrorResponseWriter.write(response, resolveTokenErrorCode(validationResult));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private ErrorCode resolveTokenErrorCode(JwtValidationResult validationResult) {
        if (validationResult == JwtValidationResult.EXPIRED) {
            return ErrorCode.ACCESS_TOKEN_EXPIRED;
        }
        return ErrorCode.INVALID_TOKEN;
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private boolean isPublicEndpoint(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();

        return path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/api-docs/")
                || path.equals("/actuator/health")
                || path.equals("/actuator/prometheus")
                || path.startsWith("/ws/")
                || is(method, HttpMethod.POST) && path.equals("/api/v1/auth/signup")
                || is(method, HttpMethod.POST) && path.equals("/api/v1/auth/login")
                || is(method, HttpMethod.POST) && path.equals("/api/v1/auth/refresh")
                || is(method, HttpMethod.POST) && path.equals("/api/v1/auth/logout")
                || is(method, HttpMethod.GET) && path.equals("/api/v1/users/me/github/connect/callback")
                || is(method, HttpMethod.POST) && path.startsWith("/api/v1/github/webhooks/");
    }

    private boolean is(String method, HttpMethod httpMethod) {
        return httpMethod.matches(method);
    }
}
