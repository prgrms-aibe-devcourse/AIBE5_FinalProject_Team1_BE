package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;

@Service
public class GithubApiClient {

    private static final String GITHUB_API_BASE = "https://api.github.com";

    private final RestClient restClient;

    public GithubApiClient(RestClient.Builder builder) {
        this.restClient = builder
                .baseUrl(GITHUB_API_BASE)
                .build();
    }

    public List<String> fetchEntitySources(String owner, String repo, String branch, String token) {
        GithubTreeResponse treeResponse = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + owner + "/" + repo + "/git/trees/" + branch)
                        .queryParam("recursive", "1")
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GithubTreeResponse.class);

        if (treeResponse == null || treeResponse.tree() == null) {
            return List.of();
        }

        return treeResponse.tree().stream()
                .filter(item -> "blob".equals(item.type()) && item.path().endsWith(".java"))
                .map(item -> fetchFileContent(owner, repo, branch, item.path(), token))
                .filter(content -> content != null && content.contains("@Entity"))
                .toList();
    }

    private String fetchFileContent(String owner, String repo, String branch, String path, String token) {
        GithubContentResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + owner + "/" + repo + "/contents/" + path)
                        .queryParam("ref", branch)
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GithubContentResponse.class);

        if (response == null || response.content() == null) return null;

        String cleanBase64 = response.content().replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(cleanBase64));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubTreeResponse(List<GithubTreeItem> tree) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubTreeItem(String path, String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubContentResponse(String content, String encoding) {}
}
