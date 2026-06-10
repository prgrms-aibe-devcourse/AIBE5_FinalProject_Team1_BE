package com.team1.codedock.domain.channel.entity;

import com.team1.codedock.domain.github.entity.GithubRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "channels")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Channel extends BaseEntity {

    public static final String TYPE_CUSTOM = "custom";

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

    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
