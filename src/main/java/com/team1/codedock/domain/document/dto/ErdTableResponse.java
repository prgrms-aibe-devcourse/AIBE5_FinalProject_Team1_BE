package com.team1.codedock.domain.document.dto;

import com.team1.codedock.domain.document.entity.ErdTable;

public record ErdTableResponse(
        Long id,
        Long workspaceId,
        String tableName,
        String schemaDefinition,
        String description
) {
    public static ErdTableResponse from(ErdTable table) {
        return new ErdTableResponse(
                table.getId(),
                table.getWorkspace().getId(),
                table.getTableName(),
                table.getSchemaDefinition(),
                table.getDescription()
        );
    }
}
