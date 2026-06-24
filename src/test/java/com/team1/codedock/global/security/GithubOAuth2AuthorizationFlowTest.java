package com.team1.codedock.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GithubOAuth2AuthorizationFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GitHub OAuth 진입 URL의 허용 옵션이 실제 redirect Location까지 전달된다")
    void githubAuthorizationEndpointRedirectsWithAllowedOptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/github")
                        .param("prompt", "select_account")
                        .param("allow_signup", "false")
                        .param("login", "jean-2077"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MultiValueMap<String, String> queryParams = redirectQueryParams(result);

        assertThat(queryParams.getFirst("prompt")).isEqualTo("select_account");
        assertThat(queryParams.getFirst("allow_signup")).isEqualTo("false");
        assertThat(queryParams.getFirst("login")).isEqualTo("jean-2077");
        assertThat(queryParams.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(queryParams.getFirst("response_type")).isEqualTo("code");
        assertThat(queryParams.getFirst("state")).isNotBlank();
        assertThat(queryParams.getFirst("redirect_uri"))
                .contains("/login/oauth2/code/github");
        assertThat(queryParams.getFirst("scope"))
                .contains("read:user")
                .contains("user:email")
                .contains("repo");
    }

    @Test
    @DisplayName("GitHub OAuth 진입 URL의 허용 외 파라미터는 실제 redirect Location에 반영되지 않는다")
    void githubAuthorizationEndpointDoesNotRedirectWithUnsafeParameters() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/github")
                        .param("prompt", "none")
                        .param("allow_signup", "yes")
                        .param("login", "bad user")
                        .param("client_id", "attacker-client")
                        .param("redirect_uri", "https://attacker.example/callback")
                        .param("scope", "admin:org")
                        .param("state", "attacker-state")
                        .param("next", "https://attacker.example"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MultiValueMap<String, String> queryParams = redirectQueryParams(result);

        assertThat(queryParams).doesNotContainKeys("prompt", "allow_signup", "login", "next");
        assertThat(queryParams.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(queryParams.getFirst("client_id")).isNotEqualTo("attacker-client");
        assertThat(queryParams.getFirst("redirect_uri")).isNotEqualTo("https://attacker.example/callback");
        assertThat(queryParams.getFirst("scope"))
                .contains("read:user")
                .contains("user:email")
                .contains("repo")
                .doesNotContain("admin:org");
        assertThat(queryParams.getFirst("state")).isNotBlank();
        assertThat(queryParams.getFirst("state")).isNotEqualTo("attacker-state");
    }

    @Test
    @DisplayName("기본 GitHub OAuth 진입 URL은 옵션 없이 기존 redirect 구조를 유지한다")
    void githubAuthorizationEndpointKeepsDefaultRedirectWithoutOptions() throws Exception {
        MvcResult result = mockMvc.perform(get("/oauth2/authorization/github"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MultiValueMap<String, String> queryParams = redirectQueryParams(result);

        assertThat(queryParams)
                .containsKeys("response_type", "client_id", "scope", "state", "redirect_uri")
                .doesNotContainKeys("prompt", "allow_signup", "login");
        assertThat(queryParams.getFirst("client_id")).isEqualTo("test-client-id");
        assertThat(queryParams.getFirst("response_type")).isEqualTo("code");
    }

    private MultiValueMap<String, String> redirectQueryParams(MvcResult result) {
        String location = result.getResponse().getHeader("Location");
        assertThat(location)
                .isNotBlank()
                .startsWith("https://github.com/login/oauth/authorize?");
        return UriComponentsBuilder.fromUriString(location)
                .build()
                .getQueryParams();
    }
}
