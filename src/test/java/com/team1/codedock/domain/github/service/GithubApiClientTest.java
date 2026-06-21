package com.team1.codedock.domain.github.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubApiClientTest {

    @Mock private RestClient.Builder builder;

    private GithubApiClient githubApiClient;

    @SuppressWarnings("rawtypes")
    private RestClient.RequestHeadersUriSpec uriSpec;
    private RestClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings({"rawtypes", "unchecked"})
    void setUp() {
        RestClient restClient = mock(RestClient.class);
        uriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(builder.baseUrl(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(restClient);

        githubApiClient = new GithubApiClient(builder);

        lenient().when(restClient.get()).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(any(Function.class))).thenReturn(uriSpec);
        lenient().when(uriSpec.uri(anyString())).thenReturn(uriSpec);
        lenient().when(uriSpec.header(anyString(), any(String[].class))).thenReturn(uriSpec);
        lenient().when(uriSpec.retrieve()).thenReturn(responseSpec);
    }

    // ── fetchEntitySources ─────────────────────────────────────

    @Test
    @DisplayName("@Entity가 포함된 .java 파일을 디코딩하여 반환한다")
    @SuppressWarnings("unchecked")
    void fetchEntitySources_성공_Entity_파일_반환() {
        String entitySource = "@Entity public class Item {}";
        String base64Content = Base64.getEncoder().encodeToString(entitySource.getBytes());

        var treeItem = new GithubApiClient.GithubTreeItem("src/main/java/Item.java", "blob");
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(treeItem));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("@Entity");
    }

    @Test
    @DisplayName("@Entity가 없는 .java 파일은 반환하지 않는다")
    @SuppressWarnings("unchecked")
    void fetchEntitySources_성공_Entity_없으면_빈리스트() {
        String nonEntitySource = "public class ItemDto {}";
        String base64Content = Base64.getEncoder().encodeToString(nonEntitySource.getBytes());

        var treeItem = new GithubApiClient.GithubTreeItem("src/main/java/ItemDto.java", "blob");
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(treeItem));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tree API 응답이 null이면 빈 리스트를 반환한다")
    void fetchEntitySources_treeResponse_null이면_빈리스트() {
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(null);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tree 필드가 null이면 빈 리스트를 반환한다")
    void fetchEntitySources_tree_null이면_빈리스트() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(null);
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName(".java 확장자가 아닌 파일은 제외한다")
    void fetchEntitySources_Java_아닌_파일_제외() {
        var treeItem = new GithubApiClient.GithubTreeItem("src/main/resources/application.yml", "blob");
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(treeItem));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("type이 blob이 아닌 항목은 제외한다")
    void fetchEntitySources_blob_아닌_타입_제외() {
        var treeItem = new GithubApiClient.GithubTreeItem("src/main/java", "tree");
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(treeItem));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("파일 내용 조회가 null이면 해당 항목을 제외한다")
    @SuppressWarnings("unchecked")
    void fetchEntitySources_fileContent_null이면_제외() {
        var treeItem = new GithubApiClient.GithubTreeItem("src/main/java/Item.java", "blob");
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(treeItem));

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(null);

        List<String> result = githubApiClient.fetchEntitySources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    // ── fetchPrimaryEmail ──────────────────────────────────────

    @Test
    @DisplayName("이메일 API 응답이 null이면 null을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchPrimaryEmail_응답_null이면_null() {
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

        String result = githubApiClient.fetchPrimaryEmail("token");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("primary이고 verified인 이메일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchPrimaryEmail_primary_verified_이메일_반환() {
        var email = new GithubApiClient.GithubEmail("primary@example.com", true, true, "public");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(email));

        String result = githubApiClient.fetchPrimaryEmail("token");

        assertThat(result).isEqualTo("primary@example.com");
    }

    @Test
    @DisplayName("primary+verified가 없으면 verified 이메일로 폴백한다")
    @SuppressWarnings("unchecked")
    void fetchPrimaryEmail_verified_폴백_이메일_반환() {
        var primaryUnverified = new GithubApiClient.GithubEmail("primary@example.com", true, false, "public");
        var verifiedNonPrimary = new GithubApiClient.GithubEmail("verified@example.com", false, true, "public");
        when(responseSpec.body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(primaryUnverified, verifiedNonPrimary));

        String result = githubApiClient.fetchPrimaryEmail("token");

        assertThat(result).isEqualTo("verified@example.com");
    }

    @Test
    @DisplayName("verified 이메일이 없으면 null을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchPrimaryEmail_verified_없으면_null() {
        var unverified = new GithubApiClient.GithubEmail("unverified@example.com", true, false, "public");
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(unverified));

        String result = githubApiClient.fetchPrimaryEmail("token");

        assertThat(result).isNull();
    }

    // ── fetchControllerSources ─────────────────────────────────

    @Test
    @DisplayName("pom.xml이 있고 @RestController가 포함된 .java 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchControllerSources_RestController_반환() {
        String source = "@RestController public class UserController {}";
        String base64 = Base64.getEncoder().encodeToString(source.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/UserController.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchControllerSources("owner", "repo", "main", "token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("@RestController");
    }

    @Test
    @DisplayName("pom.xml이 없으면 빈 리스트를 반환한다")
    void fetchControllerSources_pom_없으면_빈리스트() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("package.json", "blob")));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchControllerSources("owner", "repo", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("@RestController/@Controller가 없는 .java 파일은 제외한다")
    @SuppressWarnings("unchecked")
    void fetchControllerSources_어노테이션_없으면_빈리스트() {
        String source = "@Entity public class User {}";
        String base64 = Base64.getEncoder().encodeToString(source.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/User.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchControllerSources("owner", "repo", "main", "token");

        assertThat(result).isEmpty();
    }

    // ── fetchSourcesByKeyword ──────────────────────────────────

    @Test
    @DisplayName("topic이 null이면 API 호출 없이 빈 리스트를 반환한다")
    void fetchSourcesByKeyword_topic_null_빈리스트() {
        List<String> result = githubApiClient.fetchSourcesByKeyword("owner", "repo", "main", "token", null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("topic이 공백이면 API 호출 없이 빈 리스트를 반환한다")
    void fetchSourcesByKeyword_topic_blank_빈리스트() {
        List<String> result = githubApiClient.fetchSourcesByKeyword("owner", "repo", "main", "token", "   ");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("키워드가 파일 경로에 포함된 소스 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchSourcesByKeyword_키워드_매칭_파일_반환() {
        String source = "public class PaymentService {}";
        String base64 = Base64.getEncoder().encodeToString(source.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("src/payment/PaymentService.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchSourcesByKeyword("owner", "repo", "main", "token", "payment");

        assertThat(result).hasSize(1).contains("public class PaymentService {}");
    }

    @Test
    @DisplayName("소스 확장자가 아닌 파일(.png)은 제외한다")
    void fetchSourcesByKeyword_소스_아닌_확장자_제외() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("src/payment/payment.png", "blob")));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchSourcesByKeyword("owner", "repo", "main", "token", "payment");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("매칭된 파일이 11개여도 최대 10개만 반환한다")
    @SuppressWarnings("unchecked")
    void fetchSourcesByKeyword_11개_있어도_10개_반환() {
        String source = "public class UserService {}";
        String base64 = Base64.getEncoder().encodeToString(source.getBytes());

        var items = IntStream.range(0, 11)
                .mapToObj(i -> new GithubApiClient.GithubTreeItem("src/user/UserService" + i + ".java", "blob"))
                .toList();
        var treeResponse = new GithubApiClient.GithubTreeResponse(items);
        var contentResponse = new GithubApiClient.GithubContentResponse(base64, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchSourcesByKeyword("owner", "repo", "main", "token", "user");

        assertThat(result).hasSize(10);
    }

    // ── fetchCommits ───────────────────────────────────────────

    @Test
    @DisplayName("기간 내 커밋 메시지 목록을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchCommits_커밋_목록_반환() {
        var commit = new GithubApiClient.GithubCommit(
                new GithubApiClient.GithubCommitDetail("feat: 로그인 기능 추가"));
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(List.of(commit));

        List<String> result = githubApiClient.fetchCommits(
                "owner", "repo", "main", "token",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7));

        assertThat(result).hasSize(1).contains("feat: 로그인 기능 추가");
    }

    @Test
    @DisplayName("commits API 응답이 null이면 빈 리스트를 반환한다")
    @SuppressWarnings("unchecked")
    void fetchCommits_응답_null이면_빈리스트() {
        when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

        List<String> result = githubApiClient.fetchCommits(
                "owner", "repo", "main", "token",
                LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 7));

        assertThat(result).isEmpty();
    }
}
