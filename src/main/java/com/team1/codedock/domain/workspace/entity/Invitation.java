package com.team1.codedock.domain.workspace.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "invitations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_invitations")
    @SequenceGenerator(name = "seq_invitations", sequenceName = "seq_invitations", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inviter_member_id", nullable = false)
    private WorkspaceMember inviterMember;

    @Column(name = "invited_email", nullable = false, length = 255)
    private String invitedEmail;

    // 'admin' | 'editor' | 'viewer'
    @Column(name = "invited_authority", nullable = false, length = 30)
    private String invitedAuthority;

    @Column(name = "invited_position", length = 100)
    private String invitedPosition;

    @Column(nullable = false, unique = true, length = 255)
    private String token;

    // 'pending' | 'accepted' | 'rejected' | 'expired' | 'revoked'
    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private WorkspaceMember revokedBy;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    public static Invitation create(Workspace workspace, WorkspaceMember inviterMember,
                                    String invitedEmail, String invitedAuthority,
                                    String invitedPosition, String token, LocalDateTime expiresAt) {
        Invitation invitation = new Invitation();
        invitation.workspace = workspace;
        invitation.inviterMember = inviterMember;
        invitation.invitedEmail = invitedEmail;
        invitation.invitedAuthority = invitedAuthority;
        invitation.invitedPosition = invitedPosition;
        invitation.token = token;
        invitation.expiresAt = expiresAt;
        invitation.status = "pending";
        return invitation;
    }

    public void accept() {
        this.status = "accepted";
    }

    public void reject() {
        this.status = "rejected";
    }

    public void expire() {
        this.status = "expired";
    }

    public void revoke(WorkspaceMember revokedBy) {
        this.status = "revoked";
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = revokedBy;
    }
}
