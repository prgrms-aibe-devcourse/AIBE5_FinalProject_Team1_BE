package com.team1.codedock.domain.auth.handler;

import com.team1.codedock.domain.auth.dto.TokenResponse;
import com.team1.codedock.domain.auth.service.AuthService;
import com.team1.codedock.domain.auth.service.GithubOAuth2User;
import com.team1.codedock.global.security.GithubLinkTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
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
    private final GithubLinkTokenProvider githubLinkTokenProvider;

    @Value("${app.oauth2.redirect-uri:http://localhost:5173/oauth/callback}")
    private String redirectUri;

    @Value("${app.oauth2.popup-redirect-uri:http://localhost:5173/oauth/popup-callback}")
    private String popupRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        GithubOAuth2User oAuth2User = (GithubOAuth2User) authentication.getPrincipal();
        Long userId = oAuth2User.getUserId();

        if (isLinkMode(request)) {
            String linkToken = githubLinkTokenProvider.generate(userId);
            String url = UriComponentsBuilder.fromUriString(popupRedirectUri)
                    .queryParam("github_link_token", linkToken)
                    .build().toUriString();
            log.info("OAuth2 link 모드 → userId={}", userId);
            getRedirectStrategy().sendRedirect(request, response, url);
            return;
        }

        TokenResponse tokens = authService.issueTokens(userId);

        String redirectUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("access_token", tokens.accessToken())
                .queryParam("refresh_token", tokens.refreshToken())
                .build().toUriString();

        log.info("OAuth2 로그인 성공 → userId={}", userId);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }

    private boolean isLinkMode(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return false;
        for (Cookie c : cookies) {
            if ("oauth2_mode".equals(c.getName()) && "link".equals(c.getValue())) {
                return true;
            }
        }
        return false;
    }
}
