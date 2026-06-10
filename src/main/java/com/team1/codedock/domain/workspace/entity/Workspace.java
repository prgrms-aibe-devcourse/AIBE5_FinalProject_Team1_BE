package com.team1.codedock.domain.workspace.entity;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workspace extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_workspaces")
    @SequenceGenerator(name = "seq_workspaces", sequenceName = "seq_workspaces", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column(name = "logo_url", columnDefinition = "CLOB")
    private String logoUrl;

    public static Workspace create(User owner, String name, String slug, String description) {
        Workspace workspace = new Workspace();
        workspace.createdBy = owner;
        workspace.owner = owner;
        workspace.name = name;
        workspace.slug = slug;
        workspace.description = description;
        return workspace;
    }

    public void update(String name, String description) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
    }
}
