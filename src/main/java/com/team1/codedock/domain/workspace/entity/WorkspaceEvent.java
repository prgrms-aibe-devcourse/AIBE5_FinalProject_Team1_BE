package com.team1.codedock.domain.workspace.entity;

import com.team1.codedock.global.entity.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "workspace_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceEvent extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_workspace_events")
    @SequenceGenerator(name = "seq_workspace_events", sequenceName = "seq_workspace_events", allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventType type;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "pr_id")
    private Long prId;

    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "channel_id")
    private Long channelId;

    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "repository_name", length = 255)
    private String repositoryName;

    @Column(name = "thread_id")
    private Long threadId;

    @Column(name = "pr_number")
    private Long prNumber;

    @Column(name = "issue_number")
    private Long issueNumber;

    @Column(columnDefinition = "CLOB")
    private String content;

    public enum EventType {
        PR_CREATED, ISSUE_CREATED, PR_REVIEW, MENTION, REPLY
    }

    public static WorkspaceEvent create(
            Workspace workspace, EventType type, String actorName,
            Long prId, Long issueId, Long channelId, String content,
            Long repositoryId, String repositoryName, Long threadId, Long prNumber, Long issueNumber) {
        WorkspaceEvent event = new WorkspaceEvent();
        event.workspace = workspace;
        event.type = type;
        event.actorName = actorName;
        event.prId = prId;
        event.issueId = issueId;
        event.channelId = channelId;
        event.content = content;
        event.repositoryId = repositoryId;
        event.repositoryName = repositoryName;
        event.threadId = threadId;
        event.prNumber = prNumber;
        event.issueNumber = issueNumber;
        return event;
    }
}
