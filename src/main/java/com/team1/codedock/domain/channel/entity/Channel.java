package com.team1.codedock.domain.channel.entity;

import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "channels",
        uniqueConstraints = @UniqueConstraint(name = "uq_channels", columnNames = {"workspace_id", "name"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 120;
    public static final String TYPE_GENERAL = "general";
    public static final String TYPE_REPOSITORY = "repository";
    public static final String TYPE_CUSTOM = "custom";
    public static final String DEFAULT_GENERAL_NAME = "general";

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_channels")
    @SequenceGenerator(name = "seq_channels", sequenceName = "seq_channels", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "github_repository_id")
    private GithubRepository githubRepository;

    @Column(nullable = false, length = 120)
    private String name;

    // general, repository, custom 중 하나를 사용함
    @Column(name = "channel_type", nullable = false, length = 30)
    private String channelType;

    @Column(name = "is_deletable", nullable = false)
    private boolean isDeletable;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Lob
    @Column
    private String description;

    public static Channel createGeneral(Workspace workspace) {
        return createGeneral(workspace, 0);
    }

    public static Channel createGeneral(Workspace workspace, int displayOrder) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = null;
        channel.name = DEFAULT_GENERAL_NAME;
        channel.channelType = TYPE_GENERAL;
        channel.isDeletable = false;
        channel.displayOrder = displayOrder;
        channel.description = "기본 워크스페이스 채널";
        return channel;
    }

    public static Channel createCustom(Workspace workspace, String name, String description) {
        return createCustom(workspace, name, description, 0);
    }

    public static Channel createCustom(Workspace workspace, String name, String description, int displayOrder) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = null;
        channel.name = name;
        channel.channelType = TYPE_CUSTOM;
        channel.isDeletable = true;
        channel.displayOrder = displayOrder;
        channel.description = description;
        return channel;
    }

    public static Channel createRepository(Workspace workspace, GithubRepository githubRepository) {
        return createRepository(workspace, githubRepository, githubRepository.getName());
    }

    public static Channel createRepository(Workspace workspace, GithubRepository githubRepository, String name) {
        return createRepository(workspace, githubRepository, name, 0);
    }

    public static Channel createRepository(
            Workspace workspace,
            GithubRepository githubRepository,
            String name,
            int displayOrder
    ) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = githubRepository;
        channel.name = name;
        channel.channelType = TYPE_REPOSITORY;
        channel.isDeletable = false;
        channel.displayOrder = displayOrder;
        channel.description = githubRepository.getDescription();
        return channel;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void updateDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
