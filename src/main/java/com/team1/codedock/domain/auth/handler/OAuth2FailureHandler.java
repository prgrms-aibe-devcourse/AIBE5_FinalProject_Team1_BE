package com.team1.codedock.domain.auth.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${app.oauth2.redirect-uri:http://localhost:5173/oauth/callback}")
    private String redirectUri;

    @Value("${app.oauth2.popup-redirect-uri:http://localhost:5173/oauth/popup-callback}")
    private String popupRedirectUri;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.error("OAuth2 로그인 실패: {}", exception.getMessage());
        String target = isLinkMode(request) ? popupRedirectUri : redirectUri;
        String redirectUrl = UriComponentsBuilder.fromUriString(target)
                .queryParam("error", "oauth_failed")
                .build().toUriString();
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
