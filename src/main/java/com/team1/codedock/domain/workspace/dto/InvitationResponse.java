package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.Invitation;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class InvitationResponse {
    private Long invitationId;
    private String email;
    private String role;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    public static InvitationResponse from(Invitation invitation) {
        return InvitationResponse.builder()
                .invitationId(invitation.getId())
                .email(invitation.getInvitedEmail())
                .role(invitation.getInvitedAuthority())
                .status(invitation.getStatus())
                .expiresAt(invitation.getExpiresAt())
                .createdAt(invitation.getCreatedAt())
                .build();
    }
}