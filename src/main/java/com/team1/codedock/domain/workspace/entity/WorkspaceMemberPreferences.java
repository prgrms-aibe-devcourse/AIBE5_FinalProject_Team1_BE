package com.team1.codedock.domain.workspace.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_member_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WorkspaceMemberPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_wmp")
    @SequenceGenerator(name = "seq_wmp", sequenceName = "seq_wmp", allocationSize = 1)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_member_id", nullable = false, unique = true)
    private WorkspaceMember workspaceMember;

    // 'all' | 'mentions' | 'muted'
    @Column(name = "notification_mode", nullable = false, length = 20)
    private String notificationMode = "mentions";

    // 'active' | 'away' | 'busy' | 'offline'
    @Column(nullable = false, length = 20)
    private String presence = "active";

    public static WorkspaceMemberPreferences create(WorkspaceMember member) {
        WorkspaceMemberPreferences p = new WorkspaceMemberPreferences();
        p.workspaceMember = member;
        p.notificationMode = "mentions";
        p.presence = "active";
        return p;
    }

    public void updatePresence(String presence) {
        this.presence = presence;
    }

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
