package com.team1.codedock.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GithubOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private static final String AUTHORIZATION_BASE_URI = "/oauth2/authorization";
    private static final String GITHUB_REGISTRATION_ID = "github";
    private static final String PROMPT_SELECT_ACCOUNT = "select_account";
    private static final Pattern GITHUB_LOGIN_PATTERN =
            Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}");

    private final OAuth2AuthorizationRequestResolver delegate;

    public GithubOAuth2AuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository,
                AUTHORIZATION_BASE_URI
        );
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request);
        return customizeGithubAuthorizationRequest(request, authorizationRequest, resolveRegistrationId(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authorizationRequest = delegate.resolve(request, clientRegistrationId);
        return customizeGithubAuthorizationRequest(request, authorizationRequest, clientRegistrationId);
    }

    private OAuth2AuthorizationRequest customizeGithubAuthorizationRequest(
            HttpServletRequest request,
            OAuth2AuthorizationRequest authorizationRequest,
            String clientRegistrationId
    ) {
        if (authorizationRequest == null || !GITHUB_REGISTRATION_ID.equals(clientRegistrationId)) {
            return authorizationRequest;
        }

        Map<String, Object> githubOptions = resolveGithubOptions(request);
        if (githubOptions.isEmpty()) {
            return authorizationRequest;
        }

        String authorizationRequestUri = appendGithubOptions(
                authorizationRequest.getAuthorizationRequestUri(),
                githubOptions
        );

        return OAuth2AuthorizationRequest.from(authorizationRequest)
                // 프론트 요청값을 그대로 믿지 않고 GitHub OAuth에서 허용한 옵션만 전달함.
                .additionalParameters(parameters -> parameters.putAll(githubOptions))
                .authorizationRequestUri(authorizationRequestUri)
                .build();
    }

    private Map<String, Object> resolveGithubOptions(HttpServletRequest request) {
        Map<String, Object> options = new LinkedHashMap<>();

        String prompt = request.getParameter("prompt");
        if (PROMPT_SELECT_ACCOUNT.equals(prompt)) {
            options.put("prompt", PROMPT_SELECT_ACCOUNT);
        }

        String allowSignup = request.getParameter("allow_signup");
        if ("true".equalsIgnoreCase(allowSignup) || "false".equalsIgnoreCase(allowSignup)) {
            options.put("allow_signup", allowSignup.toLowerCase(Locale.ROOT));
        }

        String login = request.getParameter("login");
        if (login != null && GITHUB_LOGIN_PATTERN.matcher(login).matches()) {
            options.put("login", login);
        }

        return options;
    }

    private String appendGithubOptions(String authorizationRequestUri, Map<String, Object> githubOptions) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(authorizationRequestUri);
        githubOptions.forEach((key, value) -> builder.replaceQueryParam(key, value));
        return builder.build(true).toUriString();
    }

    private String resolveRegistrationId(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        String prefix = AUTHORIZATION_BASE_URI + "/";
        if (!path.startsWith(prefix)) {
            return null;
        }
        return path.substring(prefix.length());
    }
}
