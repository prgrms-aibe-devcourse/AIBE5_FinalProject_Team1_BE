package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkspaceMemberResponse {

    private Long memberId;
    private Long userId;
    private String email;
    private String username;
    private String role;
    private LocalDateTime joinedAt;
    private String presence;

    public static WorkspaceMemberResponse from(WorkspaceMember member) {
        return WorkspaceMemberResponse.builder()
                .memberId(member.getId())
                .userId(member.getUser().getId())
                .email(member.getUser().getEmail())
                .username(member.getUser().getUsername())
                .role(member.getAuthority())
                .joinedAt(member.getCreatedAt())
                .presence("active")
                .build();
    }

    public static WorkspaceMemberResponse from(WorkspaceMember member, String presence) {
        return WorkspaceMemberResponse.builder()
                .memberId(member.getId())
                .userId(member.getUser().getId())
                .email(member.getUser().getEmail())
                .username(member.getUser().getUsername())
                .role(member.getAuthority())
                .joinedAt(member.getCreatedAt())
                .presence(presence != null ? presence : "active")
                .build();
    }
}