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

    // ── fetchRepoSources ───────────────────────────────────────

    @Test
    @DisplayName("pom.xml이 있는 Java 레포에서 @Entity 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_Java_레포_Entity_파일_반환() {
        String entitySource = "@Entity public class Item {}";
        String base64Content = Base64.getEncoder().encodeToString(entitySource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/java/Item.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("@Entity");
    }

    @Test
    @DisplayName("pom.xml이 있는 Java 레포에서 @Entity가 없는 파일은 반환하지 않는다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_Java_레포_Entity_없으면_빈리스트() {
        String nonEntitySource = "public class ItemDto {}";
        String base64Content = Base64.getEncoder().encodeToString(nonEntitySource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/java/ItemDto.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tree API 응답이 null이면 빈 리스트를 반환한다")
    void fetchRepoSources_treeResponse_null이면_빈리스트() {
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(null);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("tree 필드가 null이면 빈 리스트를 반환한다")
    void fetchRepoSources_tree_null이면_빈리스트() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(null);
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pom.xml이 있는 Java 레포에서 .java가 아닌 파일은 제외한다")
    void fetchRepoSources_Java_레포_java_아닌_파일_제외() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/resources/application.yml", "blob")));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pom.xml이 있는 Java 레포에서 type이 blob이 아닌 항목은 제외한다")
    void fetchRepoSources_Java_레포_blob_아닌_타입_제외() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/java", "tree")));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("pom.xml이 있는 Java 레포에서 파일 내용이 null이면 제외한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_Java_레포_fileContent_null이면_제외() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("pom.xml", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/java/Item.java", "blob")));

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(null);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("build.gradle이 있는 Java 레포에서 @Entity 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_build_gradle_Java_레포_Entity_반환() {
        String entitySource = "@Entity public class User {}";
        String base64Content = Base64.getEncoder().encodeToString(entitySource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("build.gradle", "blob"),
                new GithubApiClient.GithubTreeItem("src/main/java/User.java", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("@Entity");
    }

    @Test
    @DisplayName("기타 언어 레포에서 ORM 폴더 내 .py 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_기타_언어_ORM_폴더_파일_반환() {
        String modelSource = "class User(Base): pass";
        String base64Content = Base64.getEncoder().encodeToString(modelSource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("models/user.py", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1).contains(modelSource);
    }

    @Test
    @DisplayName("기타 언어 레포에서 .sql 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_기타_언어_SQL_파일_반환() {
        String sqlSource = "CREATE TABLE users (id BIGINT PRIMARY KEY);";
        String base64Content = Base64.getEncoder().encodeToString(sqlSource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("schema.sql", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1).contains(sqlSource);
    }

    @Test
    @DisplayName("기타 언어 레포에서 마이그레이션 폴더 내 .py 파일을 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_기타_언어_마이그레이션_파일_반환() {
        String migSource = "def upgrade(): op.create_table('users')";
        String base64Content = Base64.getEncoder().encodeToString(migSource.getBytes());

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("migrations/env.py", "blob")));
        var contentResponse = new GithubApiClient.GithubContentResponse(base64Content, "base64");

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class)).thenReturn(contentResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(1).contains(migSource);
    }

    @Test
    @DisplayName("기타 언어 레포에서 ORM 파일과 SQL 파일이 모두 있으면 합쳐서 반환한다")
    @SuppressWarnings("unchecked")
    void fetchRepoSources_기타_언어_ORM_SQL_합쳐서_반환() {
        String modelSource = "class User(Base): pass";
        String sqlSource = "CREATE TABLE users (id BIGINT PRIMARY KEY);";

        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("models/user.py", "blob"),
                new GithubApiClient.GithubTreeItem("schema.sql", "blob")));

        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);
        when(responseSpec.body(GithubApiClient.GithubContentResponse.class))
                .thenReturn(new GithubApiClient.GithubContentResponse(
                        Base64.getEncoder().encodeToString(modelSource.getBytes()), "base64"))
                .thenReturn(new GithubApiClient.GithubContentResponse(
                        Base64.getEncoder().encodeToString(sqlSource.getBytes()), "base64"));

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

        assertThat(result).hasSize(2).contains(modelSource, sqlSource);
    }

    @Test
    @DisplayName("기타 언어 레포에서 ORM/SQL 파일이 없으면 빈 리스트를 반환한다")
    void fetchRepoSources_기타_언어_소스_없으면_빈리스트() {
        var treeResponse = new GithubApiClient.GithubTreeResponse(List.of(
                new GithubApiClient.GithubTreeItem("README.md", "blob"),
                new GithubApiClient.GithubTreeItem(".gitignore", "blob")));
        when(responseSpec.body(GithubApiClient.GithubTreeResponse.class)).thenReturn(treeResponse);

        List<String> result = githubApiClient.fetchRepoSources("octocat", "hello-world", "main", "token");

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
