package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ErdDocumentTest {

    private Workspace workspace;
    private WorkspaceMember member;

    @BeforeEach
    void setUp() {
        workspace = mock(Workspace.class);
        member = mock(WorkspaceMember.class);
    }

    // ── create() ──────────────────────────────────────────────

    @Test
    @DisplayName("create()로 ERD 문서를 생성하면 모든 필드가 정상적으로 설정된다")
    void create_모든_필드_설정() {
        ErdDocument doc = ErdDocument.create(workspace, member, "ERD", "설명", "erDiagram\n...");

        assertThat(doc.getWorkspace()).isEqualTo(workspace);
        assertThat(doc.getCreatedBy()).isEqualTo(member);
        assertThat(doc.getTitle()).isEqualTo("ERD");
        assertThat(doc.getDescription()).isEqualTo("설명");
        assertThat(doc.getMermaidCode()).isEqualTo("erDiagram\n...");
        assertThat(doc.getDeletedAt()).isNull();
    }

    // ── update() ──────────────────────────────────────────────

    @Test
    @DisplayName("update()로 모든 필드를 수정한다")
    void update_모든_필드_수정() {
        ErdDocument doc = ErdDocument.create(workspace, member, "원래 제목", "원래 설명", "old_code");

        doc.update("수정된 제목", "수정된 설명", "new_code");

        assertThat(doc.getTitle()).isEqualTo("수정된 제목");
        assertThat(doc.getDescription()).isEqualTo("수정된 설명");
        assertThat(doc.getMermaidCode()).isEqualTo("new_code");
    }

    @Test
    @DisplayName("update() 시 null 필드는 기존 값을 유지한다")
    void update_null_필드는_기존값_유지() {
        ErdDocument doc = ErdDocument.create(workspace, member, "원래 제목", "원래 설명", "old_code");

        doc.update(null, null, null);

        assertThat(doc.getTitle()).isEqualTo("원래 제목");
        assertThat(doc.getDescription()).isEqualTo("원래 설명");
        assertThat(doc.getMermaidCode()).isEqualTo("old_code");
    }

    @Test
    @DisplayName("update() 시 일부 필드만 수정하면 나머지는 유지된다")
    void update_일부_필드만_수정() {
        ErdDocument doc = ErdDocument.create(workspace, member, "원래 제목", "원래 설명", "old_code");

        doc.update("수정된 제목", null, null);

        assertThat(doc.getTitle()).isEqualTo("수정된 제목");
        assertThat(doc.getDescription()).isEqualTo("원래 설명");
        assertThat(doc.getMermaidCode()).isEqualTo("old_code");
    }
}
