package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ApiSpecTest {

    private Workspace workspace;
    private WorkspaceMember member;

    @BeforeEach
    void setUp() {
        workspace = mock(Workspace.class);
        member = mock(WorkspaceMember.class);
    }

    // ── create() ──────────────────────────────────────────────

    @Test
    @DisplayName("create()로 API 명세를 생성하면 필수 필드가 정상적으로 설정된다")
    void create_필수_필드_설정() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThat(spec.getWorkspace()).isEqualTo(workspace);
        assertThat(spec.getCreatedBy()).isEqualTo(member);
        assertThat(spec.getTitle()).isEqualTo("회원 조회");
        assertThat(spec.getMethod()).isEqualTo("GET");
        assertThat(spec.getEndpoint()).isEqualTo("/api/users/{id}");
    }

    @Test
    @DisplayName("create() 시 status가 null이면 기본값 'design'으로 설정된다")
    void create_status_null이면_기본값_design() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThat(spec.getStatus()).isEqualTo("design");
    }

    @Test
    @DisplayName("create() 시 sourceType이 null이면 기본값 'manual'로 설정된다")
    void create_sourceType_null이면_기본값_manual() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThat(spec.getSourceType()).isEqualTo("manual");
    }

    @Test
    @DisplayName("create() 시 optional 필드에 값을 전달하면 정상적으로 설정된다")
    void create_optional_필드_설정() {
        WorkspaceMember assignee = mock(WorkspaceMember.class);

        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "회원 조회", "GET", "/api/users/{id}",
                "User", "User", "단건 조회", "상세 설명",
                "in_progress", assignee,
                "{id}", null, null, null, "{id, name}", 200,
                "v1", "manual", null, null, "비고"
        );

        assertThat(spec.getGroupName()).isEqualTo("User");
        assertThat(spec.getEntity()).isEqualTo("User");
        assertThat(spec.getSummary()).isEqualTo("단건 조회");
        assertThat(spec.getDescription()).isEqualTo("상세 설명");
        assertThat(spec.getStatus()).isEqualTo("in_progress");
        assertThat(spec.getAssignee()).isEqualTo(assignee);
        assertThat(spec.getResponseStatus()).isEqualTo(200);
        assertThat(spec.getVersion()).isEqualTo("v1");
        assertThat(spec.getNote()).isEqualTo("비고");
    }

    // ── update() ──────────────────────────────────────────────

    @Test
    @DisplayName("update()로 모든 필드를 수정한다")
    void update_모든_필드_수정() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "원래 제목", "GET", "/api/old",
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        spec.update(
                "수정된 제목", "POST", "/api/new",
                "Auth", "Token", "로그인", "상세",
                "completed", null,
                null, null, null, "{email}", "{token}", 201,
                "v2", "github", null, null, "수정 비고"
        );

        assertThat(spec.getTitle()).isEqualTo("수정된 제목");
        assertThat(spec.getMethod()).isEqualTo("POST");
        assertThat(spec.getEndpoint()).isEqualTo("/api/new");
        assertThat(spec.getGroupName()).isEqualTo("Auth");
        assertThat(spec.getStatus()).isEqualTo("completed");
        assertThat(spec.getResponseStatus()).isEqualTo(201);
        assertThat(spec.getVersion()).isEqualTo("v2");
    }

    @Test
    @DisplayName("update() 시 일부 필드만 수정하면 나머지는 유지된다")
    void update_일부_필드만_수정() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "원래 제목", "GET", "/api/users",
                "User", null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        spec.update(
                "수정된 제목", null, null,
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThat(spec.getTitle()).isEqualTo("수정된 제목");
        assertThat(spec.getMethod()).isEqualTo("GET");
        assertThat(spec.getEndpoint()).isEqualTo("/api/users");
        assertThat(spec.getGroupName()).isEqualTo("User");
    }

    @Test
    @DisplayName("update() 시 null 필드는 기존 값을 유지한다")
    void update_null_필드는_기존값_유지() {
        ApiSpec spec = ApiSpec.create(
                workspace, member,
                "원래 제목", "GET", "/api/users",
                "User", null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        spec.update(
                null, null, null,
                null, null, null, null,
                null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertThat(spec.getTitle()).isEqualTo("원래 제목");
        assertThat(spec.getMethod()).isEqualTo("GET");
        assertThat(spec.getEndpoint()).isEqualTo("/api/users");
        assertThat(spec.getGroupName()).isEqualTo("User");
    }

    // ── createFromSwagger() ───────────────────────────────────

    @Test
    @DisplayName("createFromSwagger()로 생성 시 status가 'completed', sourceType이 'swagger'로 설정된다")
    void createFromSwagger_status_sourceType_설정() {
        ApiSpec spec = ApiSpec.createFromSwagger(
                workspace, member,
                "사용자 조회", "GET", "/api/users/{id}",
                "User", "단건 조회", "상세 설명",
                "{id}", null, null, null, "{id,name}", 200
        );

        assertThat(spec.getTitle()).isEqualTo("사용자 조회");
        assertThat(spec.getMethod()).isEqualTo("GET");
        assertThat(spec.getEndpoint()).isEqualTo("/api/users/{id}");
        assertThat(spec.getGroupName()).isEqualTo("User");
        assertThat(spec.getStatus()).isEqualTo("completed");
        assertThat(spec.getSourceType()).isEqualTo("swagger");
        assertThat(spec.getResponseStatus()).isEqualTo(200);
    }

    // ── createFromAi() ────────────────────────────────────────

    @Test
    @DisplayName("createFromAi()로 생성 시 status가 'design', sourceType이 'AI'로 설정된다")
    void createFromAi_status_sourceType_설정() {
        ApiSpec spec = ApiSpec.createFromAi(
                workspace, member,
                "누락된 API", "POST", "/api/items",
                "Item", "아이템 생성", "상세 설명"
        );

        assertThat(spec.getTitle()).isEqualTo("누락된 API");
        assertThat(spec.getMethod()).isEqualTo("POST");
        assertThat(spec.getEndpoint()).isEqualTo("/api/items");
        assertThat(spec.getGroupName()).isEqualTo("Item");
        assertThat(spec.getStatus()).isEqualTo("design");
        assertThat(spec.getSourceType()).isEqualTo("AI");
    }

    // ── complete() ────────────────────────────────────────────

    @Test
    @DisplayName("complete() 호출 시 status가 'completed'로 변경된다")
    void complete_status_completed로_변경() {
        ApiSpec spec = ApiSpec.createFromAi(
                workspace, member,
                "누락된 API", "POST", "/api/items",
                null, null, null
        );
        assertThat(spec.getStatus()).isEqualTo("design");

        spec.complete();

        assertThat(spec.getStatus()).isEqualTo("completed");
    }
}
