package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.Invitation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReceivedInviteResponse {

    private Long invitationId;
    private String token;
    private String workspaceName;
    private String inviterName;
    private String role;
    private LocalDateTime expiresAt;
    private int memberCount;

    public static ReceivedInviteResponse from(Invitation invitation, int memberCount) {
        return ReceivedInviteResponse.builder()
                .invitationId(invitation.getId())
                .token(invitation.getToken())
                .workspaceName(invitation.getWorkspace().getName())
                .inviterName(invitation.getInviterMember().getUser().getUsername())
                .role(invitation.getInvitedAuthority())
                .expiresAt(invitation.getExpiresAt())
                .memberCount(memberCount)
                .build();
    }
}