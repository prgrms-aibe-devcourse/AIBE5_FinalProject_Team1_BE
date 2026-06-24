package com.team1.codedock.domain.workspace.entity;

import com.team1.codedock.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceEventTest {

    @Test
    @DisplayName("getNavigationType - PR 식별자가 있으면 PR 이동 타입을 반환한다")
    void getNavigationType_pr() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED);
        ReflectionTestUtils.setField(event, "prId", 1L);

        assertThat(event.getNavigationType()).isEqualTo("PR");
    }

    @Test
    @DisplayName("getNavigationType - 이슈 식별자가 있으면 ISSUE 이동 타입을 반환한다")
    void getNavigationType_issue() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.ISSUE_CREATED);
        ReflectionTestUtils.setField(event, "issueId", 2L);

        assertThat(event.getNavigationType()).isEqualTo("ISSUE");
    }

    @Test
    @DisplayName("getNavigationType - 답글 이벤트에 스레드 식별자가 있으면 THREAD 이동 타입을 반환한다")
    void getNavigationType_reply() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.REPLY);
        ReflectionTestUtils.setField(event, "threadId", 3L);

        assertThat(event.getNavigationType()).isEqualTo("THREAD");
    }

    @Test
    @DisplayName("getNavigationType - 멘션 이벤트에 스레드 식별자가 있으면 MENTION 이동 타입을 반환한다")
    void getNavigationType_mention() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION);
        ReflectionTestUtils.setField(event, "threadId", 4L);

        assertThat(event.getNavigationType()).isEqualTo("MENTION");
    }

    @Test
    @DisplayName("getNavigationType - 구체적인 타깃 없이 채널만 있으면 CHANNEL로 대체한다")
    void getNavigationType_channelFallback() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED);
        ReflectionTestUtils.setField(event, "channelId", 5L);

        assertThat(event.getNavigationType()).isEqualTo("CHANNEL");
    }

    @Test
    @DisplayName("getNavigationType - 이동 타깃이 전혀 없으면 WORKSPACE로 대체한다")
    void getNavigationType_workspaceFallback() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.PR_CREATED);

        assertThat(event.getNavigationType()).isEqualTo("WORKSPACE");
    }

    @Test
    @DisplayName("getDisplayOccurredAt - 실제 발생 시각이 있으면 그 값을 우선한다")
    void getDisplayOccurredAt_prefersOccurredAt() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION);
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 25, 10, 0);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 6, 24, 9, 0);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);
        ReflectionTestUtils.setField(event, "occurredAt", occurredAt);

        assertThat(event.getDisplayOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("getDisplayOccurredAt - 실제 발생 시각이 없으면 생성 시각으로 대체한다")
    void getDisplayOccurredAt_fallsBackToCreatedAt() {
        WorkspaceEvent event = event(WorkspaceEvent.EventType.MENTION);
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 25, 10, 0);
        ReflectionTestUtils.setField(event, "createdAt", createdAt);

        assertThat(event.getDisplayOccurredAt()).isEqualTo(createdAt);
    }

    private static WorkspaceEvent event(WorkspaceEvent.EventType type) {
        User owner = User.create("owner@test.com", "hashed", "owner");
        Workspace workspace = Workspace.create(owner, "팀", "team-1", null);
        return WorkspaceEvent.create(
                workspace, type, "actor", null, null, null, "content",
                null, null, null, null, null, null);
    }
}
