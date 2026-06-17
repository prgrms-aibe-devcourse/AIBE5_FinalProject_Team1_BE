package com.team1.codedock.domain.channel.entity;

import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
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

    // 'general' | 'repository' | 'custom'
    @Column(name = "channel_type", nullable = false, length = 30)
    private String channelType;

    @Column(name = "is_deletable", nullable = false)
    private boolean isDeletable;

    @Lob
    @Column
    private String description;

    public static Channel createGeneral(Workspace workspace) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = null;
        channel.name = DEFAULT_GENERAL_NAME;
        channel.channelType = TYPE_GENERAL;
        channel.isDeletable = false;
        channel.description = "Default workspace channel";
        return channel;
    }

    public static Channel createCustom(Workspace workspace, String name, String description) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = null;
        channel.name = name;
        channel.channelType = TYPE_CUSTOM;
        channel.isDeletable = true;
        channel.description = description;
        return channel;
    }

    public static Channel createRepository(Workspace workspace, GithubRepository githubRepository) {
        return createRepository(workspace, githubRepository, githubRepository.getName());
    }

    public static Channel createRepository(Workspace workspace, GithubRepository githubRepository, String name) {
        Channel channel = new Channel();
        channel.workspace = workspace;
        channel.githubRepository = githubRepository;
        channel.name = name;
        channel.channelType = TYPE_REPOSITORY;
        channel.isDeletable = false;
        channel.description = githubRepository.getDescription();
        return channel;
    }

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
