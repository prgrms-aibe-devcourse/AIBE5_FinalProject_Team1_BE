package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DocumentTest {

    private Workspace workspace;
    private WorkspaceMember member;

    @BeforeEach
    void setUp() {
        workspace = mock(Workspace.class);
        member = mock(WorkspaceMember.class);
    }

    // ── create() ──────────────────────────────────────────────

    @Test
    @DisplayName("create()로 문서를 생성하면 모든 필드가 정상적으로 설정된다")
    void create_모든_필드_설정() {
        Document document = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);

        assertThat(document.getWorkspace()).isEqualTo(workspace);
        assertThat(document.getCreatedBy()).isEqualTo(member);
        assertThat(document.getTitle()).isEqualTo("제목");
        assertThat(document.getContent()).isEqualTo("내용");
        assertThat(document.getCategory()).isEqualTo("manual");
        assertThat(document.getGeneratedBy()).isEqualTo("Manual");
        assertThat(document.getVisibility()).isEqualTo("workspace");
        assertThat(document.getRelatedPr()).isNull();
        assertThat(document.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("create() 시 visibility가 null이면 기본값 'workspace'로 설정된다")
    void create_visibility_null이면_기본값() {
        Document document = Document.create(workspace, member, "제목", "내용", "manual", null, null);

        assertThat(document.getVisibility()).isEqualTo("workspace");
    }

    @Test
    @DisplayName("create() 시 generatedBy는 항상 'Manual'로 고정된다")
    void create_generatedBy_Manual_고정() {
        Document document = Document.create(workspace, member, "제목", "내용", "release", "public", null);

        assertThat(document.getGeneratedBy()).isEqualTo("Manual");
    }

    // ── update() ──────────────────────────────────────────────

    @Test
    @DisplayName("update()로 제목, 내용, 공개 범위를 모두 수정한다")
    void update_모든_필드_수정() {
        Document document = Document.create(workspace, member, "원래 제목", "원래 내용", "manual", "workspace", null);

        document.update("수정된 제목", "수정된 내용", "public");

        assertThat(document.getTitle()).isEqualTo("수정된 제목");
        assertThat(document.getContent()).isEqualTo("수정된 내용");
        assertThat(document.getVisibility()).isEqualTo("public");
    }

    @Test
    @DisplayName("update() 시 null 필드는 기존 값을 유지한다")
    void update_null_필드는_기존값_유지() {
        Document document = Document.create(workspace, member, "원래 제목", "원래 내용", "manual", "workspace", null);

        document.update(null, null, null);

        assertThat(document.getTitle()).isEqualTo("원래 제목");
        assertThat(document.getContent()).isEqualTo("원래 내용");
        assertThat(document.getVisibility()).isEqualTo("workspace");
    }

    @Test
    @DisplayName("update() 시 일부 필드만 수정하면 나머지는 유지된다")
    void update_일부_필드만_수정() {
        Document document = Document.create(workspace, member, "원래 제목", "원래 내용", "manual", "workspace", null);

        document.update("수정된 제목", null, null);

        assertThat(document.getTitle()).isEqualTo("수정된 제목");
        assertThat(document.getContent()).isEqualTo("원래 내용");
        assertThat(document.getVisibility()).isEqualTo("workspace");
    }

    // ── softDelete() ──────────────────────────────────────────

    @Test
    @DisplayName("softDelete() 호출 시 deletedAt이 null이 아닌 값으로 설정된다")
    void softDelete_deletedAt_설정() {
        Document document = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);
        assertThat(document.getDeletedAt()).isNull();

        document.softDelete();

        assertThat(document.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("softDelete() 재호출 시 deletedAt이 갱신된다")
    void softDelete_재호출_시_갱신() throws InterruptedException {
        Document document = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);
        document.softDelete();
        var firstDeletedAt = document.getDeletedAt();

        Thread.sleep(10);
        document.softDelete();

        assertThat(document.getDeletedAt()).isAfterOrEqualTo(firstDeletedAt);
    }
}
