package com.team1.codedock.domain.github.entity;

import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "github_repositories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GithubRepository extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_github_repos")
    @SequenceGenerator(name = "seq_github_repos", sequenceName = "seq_github_repos", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "github_repo_id", nullable = false, length = 100)
    private String githubRepoId;

    @Column(nullable = false, length = 100)
    private String owner;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Lob
    @Column(nullable = false)
    private String url;

    @Lob
    @Column
    private String description;

    @Column(name = "is_private", nullable = false)
    private boolean isPrivate;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "webhook_id", length = 100)
    private String webhookId;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Lob
    @Column(name = "webhook_url")
    private String webhookUrl;

    // TEXT[] -> CLOB (JSON 배열로 저장)
    @Lob
    @Column(name = "webhook_events")
    private String webhookEvents;

    @Column(name = "webhook_active", nullable = false)
    private boolean webhookActive;

    @Column(name = "webhook_last_delivery_at")
    private LocalDateTime webhookLastDeliveryAt;

    @Column(name = "webhook_last_status", length = 50)
    private String webhookLastStatus;

    public static GithubRepository create(
            Workspace workspace,
            String githubRepoId,
            String owner,
            String name,
            String fullName,
            String url,
            String description,
            boolean isPrivate,
            String defaultBranch) {
        GithubRepository repo = new GithubRepository();
        repo.workspace = workspace;
        repo.githubRepoId = githubRepoId;
        repo.owner = owner;
        repo.name = name;
        repo.fullName = fullName;
        repo.url = url;
        repo.description = description;
        repo.isPrivate = isPrivate;
        repo.defaultBranch = defaultBranch;
        repo.webhookActive = false;
        return repo;
    }
}
