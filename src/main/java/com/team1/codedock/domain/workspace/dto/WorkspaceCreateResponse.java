package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.Workspace;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkspaceCreateResponse {

    private Long id;
    private String name;
    private String slug;
    private Long defaultChannelId;

    public static WorkspaceCreateResponse from(Workspace workspace) {
        return from(workspace, null);
    }

    public static WorkspaceCreateResponse from(Workspace workspace, Long defaultChannelId) {
        return WorkspaceCreateResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .defaultChannelId(defaultChannelId)
                .build();
    }
}
