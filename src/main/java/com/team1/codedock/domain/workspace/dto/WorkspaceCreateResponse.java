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
        return WorkspaceCreateResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .defaultChannelId(null) // 채널 개발 후 .defaultChannelId(defaultChannelId) 로 변경
                .build();
    }
}