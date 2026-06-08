package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.workspace.entity.Invitation;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InviteResponse {

    private String inviteUrl;
    private LocalDateTime expiresAt;

    public static InviteResponse from(Invitation invitation, String baseUrl) {
        return InviteResponse.builder()
                .inviteUrl(baseUrl + "/invite/" + invitation.getToken())
                .expiresAt(invitation.getExpiresAt())
                .build();
    }
}