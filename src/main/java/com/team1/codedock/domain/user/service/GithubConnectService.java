package com.team1.codedock.domain.user.service;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.security.GithubConnectTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class GithubConnectService {

    private final UserRepository userRepository;
    private final GithubConnectClient githubConnectClient;
    private final GithubConnectTokenProvider connectTokenProvider;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String clientId;

    @Value("${app.oauth2.connect-redirect-uri:http://localhost:8080/api/v1/users/me/github/connect/callback}")
    private String connectRedirectUri;

    @Value("${app.oauth2.connect-callback-uri:http://localhost:5173/oauth/connect-callback}")
    private String connectCallbackUri;

    @Transactional(readOnly = true)
    public String buildAuthorizeUrl(Long currentUserId) {
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!user.isActive()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (user.getPasswordHash() == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        String state = connectTokenProvider.generate(currentUserId);
        return UriComponentsBuilder.fromUriString("https://github.com/login/oauth/authorize")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", connectRedirectUri)
                .queryParam("scope", "read:user user:email repo read:project")
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Transactional
    public String completeConnect(String code, String state) {
        Long userId;
        try {
            userId = connectTokenProvider.validateAndGetUserId(state);
        } catch (Exception e) {
            return callbackUrl("error", "invalid_state");
        }
        try {
            if (code == null) {
                return callbackUrl("error", "missing_code");
            }
            User user = userRepository.findById(userId)
                    .orElse(null);
            if (user == null || !user.isActive()) {
                return callbackUrl("error", "invalid_user");
            }
            if (user.getPasswordHash() == null) {
                return callbackUrl("error", "email_account_required");
            }
            String accessToken = githubConnectClient.exchangeCodeForAccessToken(code);
            if (accessToken == null) {
                return callbackUrl("error", "token_exchange_failed");
            }
            GithubConnectClient.GithubIdentity identity = githubConnectClient.fetchGithubIdentity(accessToken);
            if (identity == null || identity.githubId() == null) {
                return callbackUrl("error", "identity_fetch_failed");
            }
            User owner = userRepository.findByGithubId(identity.githubId()).orElse(null);
            if (owner != null && !owner.getId().equals(user.getId())) {
                return callbackUrl("conflict", "github_already_connected");
            }
            user.linkGithub(identity.githubId(), identity.login(), identity.email(), null, accessToken);
            return callbackUrl("success", null);
        } catch (Exception e) {
            return callbackUrl("error", "unknown");
        }
    }

    private String callbackUrl(String status, String reason) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(connectCallbackUri)
                .queryParam("status", status);
        if (reason != null && !reason.isBlank()) {
            builder.queryParam("reason", reason);
        }
        return builder.build().toUriString();
    }
}
