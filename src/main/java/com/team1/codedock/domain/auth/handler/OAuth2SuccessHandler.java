package com.team1.codedock.domain.auth.handler;

import com.team1.codedock.domain.auth.dto.TokenResponse;
import com.team1.codedock.domain.auth.service.AuthService;
import com.team1.codedock.domain.auth.service.GithubOAuth2User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173/oauth/callback}")
    private String redirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        GithubOAuth2User oAuth2User = (GithubOAuth2User) authentication.getPrincipal();
        Long userId = oAuth2User.getUserId();

        TokenResponse tokens = authService.issueTokens(userId);

        String redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("access_token", tokens.accessToken())
                .queryParam("refresh_token", tokens.refreshToken())
                .build().toUriString();

        log.info("OAuth2 로그인 성공 → userId={}", userId);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
