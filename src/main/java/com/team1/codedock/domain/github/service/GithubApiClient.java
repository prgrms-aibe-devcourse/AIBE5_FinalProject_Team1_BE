package com.team1.codedock.domain.github.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.core.ParameterizedTypeReference;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
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
        GithubTreeResponse treeResponse = fetchTree(owner, repo, branch, token);
        if (treeResponse == null || treeResponse.tree() == null) return List.of();

        return treeResponse.tree().stream()
                .filter(item -> "blob".equals(item.type()) && item.path().endsWith(".java"))
                .map(item -> fetchFileContent(owner, repo, branch, item.path(), token))
                .filter(content -> content != null && content.contains("@Entity"))
                .toList();
    }

    public List<String> fetchControllerSources(String owner, String repo, String branch, String token) {
        GithubTreeResponse treeResponse = fetchTree(owner, repo, branch, token);
        if (treeResponse == null || treeResponse.tree() == null) return List.of();

        boolean isJava = treeResponse.tree().stream()
                .anyMatch(item -> "pom.xml".equals(item.path()));
        if (!isJava) return List.of();

        return treeResponse.tree().stream()
                .filter(item -> "blob".equals(item.type()) && item.path().endsWith(".java"))
                .map(item -> fetchFileContent(owner, repo, branch, item.path(), token))
                .filter(content -> content != null && (
                        content.contains("@RestController") ||
                                content.contains("@Controller")))
                .toList();
    }

    private static final int KEYWORD_SOURCE_LIMIT = 10;
    private static final List<String> SOURCE_EXTENSIONS = List.of(
            ".java", ".kt", ".py", ".js", ".ts", ".jsx", ".tsx",
            ".go", ".rb", ".php", ".cs", ".cpp", ".c", ".h",
            ".swift", ".scala", ".rs", ".html", ".css", ".scss",
            ".sql", ".yaml", ".yml", ".xml", ".json", ".md"
    );

    public List<String> fetchSourcesByKeyword(String owner, String repo, String branch, String token, String topic) {
        if (topic == null || topic.isBlank()) return List.of();

        GithubTreeResponse treeResponse = fetchTree(owner, repo, branch, token);
        if (treeResponse == null || treeResponse.tree() == null) return List.of();

        String[] keywords = topic.toLowerCase().split("\\s+");

        return treeResponse.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .filter(item -> SOURCE_EXTENSIONS.stream().anyMatch(ext -> item.path().toLowerCase().endsWith(ext)))
                .filter(item -> {
                    String lowerPath = item.path().toLowerCase();
                    for (String keyword : keywords) {
                        if (lowerPath.contains(keyword)) return true;
                    }
                    return false;
                })
                .limit(KEYWORD_SOURCE_LIMIT)
                .map(item -> fetchFileContent(owner, repo, branch, item.path(), token))
                .filter(content -> content != null)
                .toList();
    }

    // [팀원 기능] 커밋 목록 조회
    public List<String> fetchCommits(String owner, String repo, String branch, String token,
                                     LocalDate startDate, LocalDate endDate) {
        String since = startDate.atStartOfDay().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String until = endDate.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<GithubCommit> commits = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + owner + "/" + repo + "/commits")
                        .queryParam("sha", branch)
                        .queryParam("since", since)
                        .queryParam("until", until)
                        .queryParam("per_page", "100")
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubCommit>>() {});

        if (commits == null) return List.of();
        return commits.stream()
                .map(c -> c.commit().message())
                .toList();
    }

    // [준우님 기능] PR 목록 조회
    public List<GithubPrItem> fetchPullRequests(String owner, String repo, String token) {
        List<GithubPrItem> result = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + owner + "/" + repo + "/pulls")
                        .queryParam("state", "all")
                        .queryParam("per_page", "100")
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubPrItem>>() {});
        return result != null ? result : List.of();
    }

    // 단일 PR 조회 (body 포함)
    public GithubPrItem fetchSinglePullRequest(String owner, String repo, int pullNumber, String token) {
        return restClient.get()
                .uri("/repos/" + owner + "/" + repo + "/pulls/" + pullNumber)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GithubPrItem.class);
    }

    // [준우님 기능] 특정 PR의 커밋 목록 조회
    public List<GithubCommitItem> fetchPullRequestCommits(String owner, String repo, int pullNumber, String token) {
        List<GithubCommitItem> result = restClient.get()
                .uri("/repos/" + owner + "/" + repo + "/pulls/" + pullNumber + "/commits")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubCommitItem>>() {});
        return result != null ? result : List.of();
    }

    public String fetchPrimaryEmail(String token) {
        List<GithubEmail> emails = restClient.get()
                .uri("/user/emails")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<List<GithubEmail>>() {});

        if (emails == null) return null;

        return emails.stream()
                .filter(e -> e.primary() && e.verified())
                .map(GithubEmail::email)
                .findFirst()
                .or(() -> emails.stream()
                        .filter(GithubEmail::verified)
                        .map(GithubEmail::email)
                        .findFirst())
                .orElse(null);
    }

    private GithubTreeResponse fetchTree(String owner, String repo, String branch, String token) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repos/" + owner + "/" + repo + "/git/trees/" + branch)
                        .queryParam("recursive", "1")
                        .build())
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(GithubTreeResponse.class);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubEmail(String email, boolean primary, boolean verified, String visibility) {}

    // [팀원 DTO] 일반 커밋 조회용
    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubCommit(GithubCommitDetailSimple commit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GithubCommitDetailSimple(String message) {}

    // [준우님 DTO] PR 조회용 상세 데이터 구조들
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubPrItem(
            long id,
            int number,
            String title,
            String body,
            String state,
            @JsonProperty("html_url") String htmlUrl,
            GithubPrUser user,
            @JsonProperty("requested_reviewers") List<GithubPrUser> requestedReviewers,
            GithubPrBranch head,
            GithubPrBranch base,
            Integer additions,
            Integer deletions,
            @JsonProperty("changed_files") Integer changedFiles,
            Boolean merged,
            @JsonProperty("merged_at") Instant mergedAt,
            @JsonProperty("created_at") Instant createdAt,
            @JsonProperty("updated_at") Instant updatedAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubPrUser(String login) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubPrBranch(String ref) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubCommitItem(
            String sha,
            GithubCommitDetail commit
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubCommitDetail(
            String message,
            GithubCommitAuthor author
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GithubCommitAuthor(
            String name,
            String date
    ) {}
}