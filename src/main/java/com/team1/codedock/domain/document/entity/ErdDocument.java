package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "erd_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErdDocument extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_erd_documents")
    @SequenceGenerator(name = "seq_erd_documents", sequenceName = "seq_erd_documents", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private WorkspaceMember createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column
    private String description;

    @Lob
    @Column(name = "mermaid_code")
    private String mermaidCode;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
