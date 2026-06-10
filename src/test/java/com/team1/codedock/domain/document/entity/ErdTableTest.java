package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ErdTableTest {

    private Workspace workspace;
    private WorkspaceMember member;

    @BeforeEach
    void setUp() {
        workspace = mock(Workspace.class);
        member = mock(WorkspaceMember.class);
    }

    // ── create() ──────────────────────────────────────────────

    @Test
    @DisplayName("create()로 ERD 테이블을 생성하면 모든 필드가 정상적으로 설정된다")
    void create_모든_필드_설정() {
        ErdTable table = ErdTable.create(workspace, member, "users", "{}", "유저 테이블");

        assertThat(table.getWorkspace()).isEqualTo(workspace);
        assertThat(table.getCreatedBy()).isEqualTo(member);
        assertThat(table.getTableName()).isEqualTo("users");
        assertThat(table.getSchemaDefinition()).isEqualTo("{}");
        assertThat(table.getDescription()).isEqualTo("유저 테이블");
    }
}
