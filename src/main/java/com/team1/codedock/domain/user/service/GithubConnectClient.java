package com.team1.codedock.domain.user.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class GithubConnectClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GithubConnectClient(
            RestClient.Builder builder,
            @Value("${spring.security.oauth2.client.registration.github.client-id}") String clientId,
            @Value("${spring.security.oauth2.client.registration.github.client-secret}") String clientSecret) {
        this.restClient = builder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String exchangeCodeForAccessToken(String code) {
        TokenResponse response = restClient.post()
                .uri("https://github.com/login/oauth/access_token")
                .header("Accept", "application/json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", clientId, "client_secret", clientSecret, "code", code))
                .retrieve()
                .body(TokenResponse.class);
        return response == null ? null : response.accessToken();
    }

    public GithubIdentity fetchGithubIdentity(String accessToken) {
        GithubUser user = restClient.get()
                .uri("https://api.github.com/user")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GithubUser.class);
        if (user == null) {
            return null;
        }
        return new GithubIdentity(String.valueOf(user.id()), user.login(), fetchPrimaryEmail(accessToken));
    }

    private String fetchPrimaryEmail(String accessToken) {
        List<GithubEmail> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubEmail>>() {});
        if (emails == null) {
            return null;
        }
        return emails.stream()
                .filter(e -> e.primary() && e.verified())
                .map(GithubEmail::email)
                .findFirst()
                .or(() -> emails.stream().filter(GithubEmail::verified).map(GithubEmail::email).findFirst())
                .orElse(null);
    }

    public record GithubIdentity(String githubId, String login, String email) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubUser(long id, String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubEmail(String email, boolean primary, boolean verified) {}
}