package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkspaceResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private String myRole;
    private int memberCount;
    private int membersOnline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastActivityAt;
    private String logoUrl;

    public static WorkspaceResponse from(Workspace workspace, WorkspaceMember myMembership, int memberCount) {
        return from(workspace, myMembership, memberCount, 0);
    }

    public static WorkspaceResponse from(Workspace workspace, WorkspaceMember myMembership, int memberCount, int membersOnline) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .description(workspace.getDescription())
                .myRole(myMembership.getAuthority())
                .memberCount(memberCount)
                .membersOnline(membersOnline)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .lastActivityAt(workspace.getLastActivityAt())
                .build();
    }

    public static WorkspaceResponse fromDetail(Workspace workspace, WorkspaceMember myMembership, int memberCount) {
        return WorkspaceResponse.builder()
                .id(workspace.getId())
                .name(workspace.getName())
                .slug(workspace.getSlug())
                .description(workspace.getDescription())
                .myRole(myMembership.getAuthority())
                .memberCount(memberCount)
                .createdAt(workspace.getCreatedAt())
                .updatedAt(workspace.getUpdatedAt())
                .lastActivityAt(workspace.getLastActivityAt())
                .logoUrl(workspace.getLogoUrl())
                .build();
    }
}