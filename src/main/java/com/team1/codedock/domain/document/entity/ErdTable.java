package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "erd_tables")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErdTable extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_erd_tables")
    @SequenceGenerator(name = "seq_erd_tables", sequenceName = "seq_erd_tables", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private WorkspaceMember createdBy;

    @Column(name = "table_name", nullable = false, length = 150)
    private String tableName;

    // JSONB -> CLOB
    @Lob
    @Column(name = "schema_definition")
    private String schemaDefinition;

    @Lob
    @Column
    private String description;

    public static ErdTable create(
            Workspace workspace,
            WorkspaceMember createdBy,
            String tableName,
            String schemaDefinition,
            String description
    ) {
        ErdTable table = new ErdTable();
        table.workspace = workspace;
        table.createdBy = createdBy;
        table.tableName = tableName;
        table.schemaDefinition = schemaDefinition;
        table.description = description;
        return table;
    }
}
