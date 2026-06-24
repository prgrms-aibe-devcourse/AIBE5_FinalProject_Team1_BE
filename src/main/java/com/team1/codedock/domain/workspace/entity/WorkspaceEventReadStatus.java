package com.team1.codedock.domain.workspace.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "workspace_event_read_statuses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_event_id", "user_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceEventReadStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_ws_event_read_status")
    @SequenceGenerator(name = "seq_ws_event_read_status", sequenceName = "seq_ws_event_read_status", allocationSize = 1)
    private Long id;

    @Column(name = "workspace_event_id", nullable = false)
    private Long workspaceEventId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public static WorkspaceEventReadStatus create(Long workspaceEventId, Long userId) {
        WorkspaceEventReadStatus status = new WorkspaceEventReadStatus();
        status.workspaceEventId = workspaceEventId;
        status.userId = userId;
        return status;
    }
}
