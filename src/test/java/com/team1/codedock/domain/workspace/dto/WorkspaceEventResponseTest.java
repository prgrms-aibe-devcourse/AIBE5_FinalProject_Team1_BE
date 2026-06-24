package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceEventResponseTest {

    @Test
    @DisplayName("WorkspaceEventResponse.from - isRead true이면 isRead()가 true이다")
    void workspaceEventResponse_isRead_true() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, null);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, true);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - isRead false이면 isRead()가 false이다")
    void workspaceEventResponse_isRead_false() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, null);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.isRead()).isFalse();
    }

    @Test
    @DisplayName("DashboardEventResponse.from - isRead true이면 isRead()가 true이다")
    void dashboardEventResponse_isRead_true() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);

        DashboardEventResponse response = DashboardEventResponse.from(event, true);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    @DisplayName("DashboardEventResponse.from - isRead false이면 isRead()가 false이다")
    void dashboardEventResponse_isRead_false() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.isRead()).isFalse();
    }

    private static WorkspaceEvent event(WorkspaceEvent.EventType type, Long targetUserId) {
        com.team1.codedock.domain.user.entity.User owner =
                com.team1.codedock.domain.user.entity.User.create("owner@test.com", "hashed", "owner");
        Workspace workspace = Workspace.create(owner, "팀", "team-1", null);
        ReflectionTestUtils.setField(workspace, "id", 1L);
        WorkspaceEvent event = WorkspaceEvent.create(
                workspace, type, "actor", null, null, null, "content",
                null, null, null, null, null, targetUserId);
        ReflectionTestUtils.setField(event, "id", 100L);
        return event;
    }
}
