package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class GithubOAuth2AuthorizationRequestResolverTest {

    private final GithubOAuth2AuthorizationRequestResolver resolver =
            new GithubOAuth2AuthorizationRequestResolver(new InMemoryClientRegistrationRepository(
                    githubClientRegistration(),
                    googleClientRegistration()
            ));

    @Test
    @DisplayName("기본 GitHub 로그인 요청은 기존 authorization URL 구조를 유지한다")
    void resolveGithubAuthorizationRequestWithoutOptions() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAuthorizationRequestUri())
                .startsWith("https://github.com/login/oauth/authorize?");
        assertThat(queryParams(authorizationRequest))
                .containsKeys("response_type", "client_id", "scope", "state", "redirect_uri")
                .doesNotContainKeys("prompt", "allow_signup", "login", "next");
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKeys("prompt", "allow_signup", "login");
    }

    @Test
    @DisplayName("GitHub 계정 선택 옵션을 authorization URL과 additional parameter에 함께 전달한다")
    void resolveGithubAuthorizationRequestWithPromptSelectAccount() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("prompt", "select_account");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(queryParams(authorizationRequest).getFirst("prompt"))
                .isEqualTo("select_account");
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("prompt", "select_account");
    }

    @Test
    @DisplayName("GitHub 회원가입 허용 옵션은 true와 false만 소문자로 전달한다")
    void resolveGithubAuthorizationRequestWithAllowSignupOption() {
        MockHttpServletRequest trueRequest = oauthRequest("/oauth2/authorization/github");
        trueRequest.addParameter("allow_signup", "TRUE");
        MockHttpServletRequest falseRequest = oauthRequest("/oauth2/authorization/github");
        falseRequest.addParameter("allow_signup", "false");

        OAuth2AuthorizationRequest trueAuthorizationRequest = resolver.resolve(trueRequest);
        OAuth2AuthorizationRequest falseAuthorizationRequest = resolver.resolve(falseRequest);

        assertThat(queryParams(trueAuthorizationRequest).getFirst("allow_signup"))
                .isEqualTo("true");
        assertThat(trueAuthorizationRequest.getAdditionalParameters())
                .containsEntry("allow_signup", "true");
        assertThat(queryParams(falseAuthorizationRequest).getFirst("allow_signup"))
                .isEqualTo("false");
        assertThat(falseAuthorizationRequest.getAdditionalParameters())
                .containsEntry("allow_signup", "false");
    }

    @Test
    @DisplayName("GitHub 로그인 힌트는 안전한 계정명 형식일 때만 전달한다")
    void resolveGithubAuthorizationRequestWithLoginHint() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("login", "jean-2077");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(queryParams(authorizationRequest).getFirst("login"))
                .isEqualTo("jean-2077");
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("login", "jean-2077");
    }

    @Test
    @DisplayName("GitHub 로그인 옵션 3개를 한 요청에 함께 전달할 수 있다")
    void resolveGithubAuthorizationRequestWithAllAllowedOptions() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("prompt", "select_account");
        request.addParameter("allow_signup", "true");
        request.addParameter("login", "jean-2077");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        MultiValueMap<String, String> queryParams = queryParams(authorizationRequest);
        assertThat(queryParams.getFirst("prompt")).isEqualTo("select_account");
        assertThat(queryParams.getFirst("allow_signup")).isEqualTo("true");
        assertThat(queryParams.getFirst("login")).isEqualTo("jean-2077");
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("prompt", "select_account")
                .containsEntry("allow_signup", "true")
                .containsEntry("login", "jean-2077");
    }

    @Test
    @DisplayName("GitHub 로그인 힌트는 39자까지 허용하고 40자는 차단한다")
    void resolveGithubAuthorizationRequestChecksLoginLengthBoundary() {
        String validLogin = "a".repeat(39);
        String tooLongLogin = "a".repeat(40);
        MockHttpServletRequest validRequest = oauthRequest("/oauth2/authorization/github");
        validRequest.addParameter("login", validLogin);
        MockHttpServletRequest invalidRequest = oauthRequest("/oauth2/authorization/github");
        invalidRequest.addParameter("login", tooLongLogin);

        OAuth2AuthorizationRequest validAuthorizationRequest = resolver.resolve(validRequest);
        OAuth2AuthorizationRequest invalidAuthorizationRequest = resolver.resolve(invalidRequest);

        assertThat(queryParams(validAuthorizationRequest).getFirst("login"))
                .isEqualTo(validLogin);
        assertThat(validAuthorizationRequest.getAdditionalParameters())
                .containsEntry("login", validLogin);
        assertThat(queryParams(invalidAuthorizationRequest))
                .doesNotContainKey("login");
        assertThat(invalidAuthorizationRequest.getAdditionalParameters())
                .doesNotContainKey("login");
    }

    @Test
    @DisplayName("허용하지 않은 OAuth query parameter는 GitHub authorization URL에 전달하지 않는다")
    void resolveGithubAuthorizationRequestIgnoresUnknownParameters() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("prompt", "none");
        request.addParameter("allow_signup", "yes");
        request.addParameter("login", "bad user");
        request.addParameter("redirect_uri", "https://attacker.example/callback");
        request.addParameter("client_id", "attacker-client");
        request.addParameter("scope", "admin:org");
        request.addParameter("next", "https://attacker.example");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        MultiValueMap<String, String> queryParams = queryParams(authorizationRequest);
        assertThat(queryParams.getFirst("prompt")).isNotEqualTo("none");
        assertThat(queryParams.getFirst("allow_signup")).isNotEqualTo("yes");
        assertThat(queryParams.getFirst("login")).isNotEqualTo("bad user");
        assertThat(queryParams.getFirst("redirect_uri")).isNotEqualTo("https://attacker.example/callback");
        assertThat(queryParams.getFirst("client_id")).isNotEqualTo("attacker-client");
        assertThat(queryParams.get("scope")).doesNotContain("admin:org");
        assertThat(queryParams).doesNotContainKeys("next");
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKeys("prompt", "allow_signup", "login", "redirect_uri", "client_id", "scope", "next");
    }

    @Test
    @DisplayName("공격자가 core OAuth 파라미터를 보내도 provider 기본값을 덮어쓰지 않는다")
    void resolveGithubAuthorizationRequestDoesNotOverrideCoreOAuthParameters() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("client_id", "attacker-client");
        request.addParameter("redirect_uri", "https://attacker.example/callback");
        request.addParameter("response_type", "token");
        request.addParameter("scope", "admin:org");
        request.addParameter("state", "attacker-state");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        MultiValueMap<String, String> queryParams = queryParams(authorizationRequest);
        assertThat(queryParams.getFirst("client_id")).isEqualTo("github-client-id");
        assertThat(queryParams.getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/login/oauth2/code/github");
        assertThat(queryParams.getFirst("response_type")).isEqualTo("code");
        assertThat(queryParams.getFirst("scope"))
                .contains("read:user")
                .contains("user:email")
                .doesNotContain("admin:org");
        assertThat(queryParams.getFirst("state")).isNotBlank();
        assertThat(queryParams.getFirst("state")).isNotEqualTo("attacker-state");
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKeys("client_id", "redirect_uri", "response_type", "scope", "state");
    }

    @Test
    @DisplayName("GitHub이 아닌 OAuth provider에는 GitHub 전용 옵션을 전달하지 않는다")
    void resolveNonGithubAuthorizationRequestDoesNotApplyGithubOptions() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/google");
        request.addParameter("prompt", "select_account");
        request.addParameter("allow_signup", "true");
        request.addParameter("login", "jean-2077");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(authorizationRequest.getAuthorizationRequestUri())
                .startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
        assertThat(queryParams(authorizationRequest))
                .doesNotContainKeys("prompt", "allow_signup", "login");
        assertThat(authorizationRequest.getAdditionalParameters())
                .doesNotContainKeys("prompt", "allow_signup", "login");
    }

    @Test
    @DisplayName("등록 id를 직접 넘기는 resolver 경로에서도 GitHub 옵션을 동일하게 전달한다")
    void resolveWithExplicitClientRegistrationIdAppliesGithubOptions() {
        MockHttpServletRequest request = oauthRequest("/oauth2/authorization/github");
        request.addParameter("prompt", "select_account");
        request.addParameter("allow_signup", "false");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request, "github");

        assertThat(queryParams(authorizationRequest).getFirst("prompt"))
                .isEqualTo("select_account");
        assertThat(queryParams(authorizationRequest).getFirst("allow_signup"))
                .isEqualTo("false");
        assertThat(authorizationRequest.getAdditionalParameters())
                .containsEntry("prompt", "select_account")
                .containsEntry("allow_signup", "false");
    }

    @Test
    @DisplayName("context path가 있어도 GitHub registration id를 올바르게 판별한다")
    void resolveGithubAuthorizationRequestWithContextPath() {
        MockHttpServletRequest request = oauthRequest("/codedock/oauth2/authorization/github");
        request.setContextPath("/codedock");
        request.setServletPath("/oauth2/authorization/github");
        request.addParameter("prompt", "select_account");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNotNull();
        assertThat(queryParams(authorizationRequest).getFirst("prompt"))
                .isEqualTo("select_account");
        assertThat(queryParams(authorizationRequest).getFirst("redirect_uri"))
                .isEqualTo("http://localhost:8080/codedock/login/oauth2/code/github");
    }

    @Test
    @DisplayName("OAuth authorization 요청 경로가 아니면 authorization request를 만들지 않는다")
    void resolveNonAuthorizationPathReturnsNull() {
        MockHttpServletRequest request = oauthRequest("/api/v1/auth/login");
        request.addParameter("prompt", "select_account");

        OAuth2AuthorizationRequest authorizationRequest = resolver.resolve(request);

        assertThat(authorizationRequest).isNull();
    }

    private MockHttpServletRequest oauthRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);
        return request;
    }

    private MultiValueMap<String, String> queryParams(OAuth2AuthorizationRequest authorizationRequest) {
        return UriComponentsBuilder.fromUriString(authorizationRequest.getAuthorizationRequestUri())
                .build()
                .getQueryParams();
    }

    private static ClientRegistration githubClientRegistration() {
        return ClientRegistration.withRegistrationId("github")
                .clientId("github-client-id")
                .clientSecret("github-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("read:user", "user:email")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("id")
                .clientName("GitHub")
                .build();
    }

    private static ClientRegistration googleClientRegistration() {
        return ClientRegistration.withRegistrationId("google")
                .clientId("google-client-id")
                .clientSecret("google-client-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
    }
}
