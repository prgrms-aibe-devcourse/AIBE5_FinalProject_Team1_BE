package com.team1.codedock.domain.workspace.entity;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "workspace_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceMember extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_workspace_members")
    @SequenceGenerator(name = "seq_workspace_members", sequenceName = "seq_workspace_members", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 'owner' | 'admin' | 'editor' | 'viewer'
    @Column(nullable = false, length = 30)
    private String authority;

    @Column(length = 100)
    private String position;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "left_reason", length = 255)
    private String leftReason;
}
