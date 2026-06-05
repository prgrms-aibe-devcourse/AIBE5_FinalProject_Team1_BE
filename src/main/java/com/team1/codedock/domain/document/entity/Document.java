package com.team1.codedock.domain.document.entity;

import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_documents")
    @SequenceGenerator(name = "seq_documents", sequenceName = "seq_documents", allocationSize = 1)
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
    private String content;

    // 'pr-summary' | 'manual' | 'meeting' | 'release'
    @Column(length = 50)
    private String category;

    // 'AI' | 'Template' | 'Manual'
    @Column(name = "generated_by", length = 20)
    private String generatedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_pr_id")
    private GithubPullRequest relatedPr;

    // 'workspace' | 'private' | 'public'
    @Column(nullable = false, length = 30)
    private String visibility = "workspace";

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
