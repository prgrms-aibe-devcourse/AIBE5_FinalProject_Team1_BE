package com.team1.codedock.domain.workspace.dto;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceEventResponseTest {

    @Test
    @DisplayName("WorkspaceEventResponse.from - 읽음 상태 true를 그대로 반환한다")
    void workspaceEventResponse_isRead_true() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, null);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, true);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - 읽음 상태 false를 그대로 반환한다")
    void workspaceEventResponse_isRead_false() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, null);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.isRead()).isFalse();
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 읽음 상태 true를 그대로 반환한다")
    void dashboardEventResponse_isRead_true() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);

        DashboardEventResponse response = DashboardEventResponse.from(event, true);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 읽음 상태 false를 그대로 반환한다")
    void dashboardEventResponse_isRead_false() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.isRead()).isFalse();
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - occurredAt이 있으면 실제 발생 시각을 우선 반환한다")
    void workspaceEventResponse_prefersOccurredAt() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, 2L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 25, 10, 0);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 24, 9, 30);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        ReflectionTestUtils.setField(event, "occurredAt", occurredAt);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - occurredAt이 없으면 createdAt으로 대체한다")
    void workspaceEventResponse_fallsBackToCreatedAt() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, 2L);
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 25, 10, 0);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.occurredAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("DashboardEventResponse.from - PR 생성 이벤트는 PR 이동 타입을 반환한다")
    void dashboardEventResponse_prNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);
        ReflectionTestUtils.setField(event, "prId", 10L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("PR");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - PR 리뷰 이벤트도 PR 이동 타입을 반환한다")
    void dashboardEventResponse_prReviewNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_REVIEW, 2L);
        ReflectionTestUtils.setField(event, "prId", 10L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("PR");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - Issue 생성 이벤트는 Issue 이동 타입을 반환한다")
    void dashboardEventResponse_issueNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.ISSUE_CREATED, null);
        ReflectionTestUtils.setField(event, "issueId", 20L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("ISSUE");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 답글 이벤트는 스레드 이동 타입을 반환한다")
    void dashboardEventResponse_threadNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.REPLY, 2L);
        ReflectionTestUtils.setField(event, "threadId", 30L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("THREAD");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 멘션 이벤트는 멘션 이동 타입을 반환한다")
    void dashboardEventResponse_mentionNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, 2L);
        ReflectionTestUtils.setField(event, "threadId", 30L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("MENTION");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 구체 타깃 없이 채널만 있으면 채널 이동 타입으로 대체한다")
    void dashboardEventResponse_channelFallbackNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);
        ReflectionTestUtils.setField(event, "channelId", 40L);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("CHANNEL");
    }

    @Test
    @DisplayName("DashboardEventResponse.from - 타깃 정보가 없으면 워크스페이스 이동 타입으로 대체한다")
    void dashboardEventResponse_workspaceFallbackNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED, null);

        DashboardEventResponse response = DashboardEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("WORKSPACE");
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - 멘션 이벤트는 멘션 이동 타입을 반환한다")
    void workspaceEventResponse_mentionNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION, 2L);
        ReflectionTestUtils.setField(event, "threadId", 30L);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("MENTION");
    }

    @Test
    @DisplayName("WorkspaceEventResponse.from - 구체 타깃 없이 채널만 있으면 채널 이동 타입으로 대체한다")
    void workspaceEventResponse_channelFallbackNavigationType() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.ISSUE_CREATED, null);
        ReflectionTestUtils.setField(event, "channelId", 40L);

        WorkspaceEventResponse response = WorkspaceEventResponse.from(event, false);

        assertThat(response.navigationType()).isEqualTo("CHANNEL");
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
