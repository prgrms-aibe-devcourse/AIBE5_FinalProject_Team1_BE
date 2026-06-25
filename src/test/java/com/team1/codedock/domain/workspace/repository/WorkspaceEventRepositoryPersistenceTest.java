package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.user.repository.UserRepository;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import com.team1.codedock.global.config.JpaConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
class WorkspaceEventRepositoryPersistenceTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceEventRepository workspaceEventRepository;

    @Test
    @DisplayName("findDashboardEvents - occurredAt이 있으면 createdAt보다 우선해 최신순 정렬한다")
    void findDashboardEvents_ordersByOccurredAtThenCreatedAtFallback() {
        Workspace workspace = seedWorkspace("event-order@test.com", "event-order");
        LocalDateTime now = LocalDateTime.of(2026, 6, 25, 12, 0);
        WorkspaceEvent oldOriginalEvent = saveEvent(workspace, WorkspaceEvent.EventType.PR_CREATED,
                1L, null, null, null, now.minusYears(1));
        WorkspaceEvent createdAtFallbackEvent = saveEvent(workspace, WorkspaceEvent.EventType.ISSUE_CREATED,
                null, 2L, null, null, null);
        WorkspaceEvent futureOriginalEvent = saveEvent(workspace, WorkspaceEvent.EventType.PR_CREATED,
                3L, null, null, null, now.plusDays(1));
        em.flush();
        em.clear();

        List<WorkspaceEvent> result = workspaceEventRepository.findDashboardEvents(
                List.of(workspace.getId()),
                99L,
                List.of(WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED, WorkspaceEvent.EventType.PR_REVIEW),
                List.of(WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION),
                PageRequest.of(0, 10)
        );

        assertThat(result).extracting(WorkspaceEvent::getId)
                .containsExactly(
                        futureOriginalEvent.getId(),
                        createdAtFallbackEvent.getId(),
                        oldOriginalEvent.getId()
                );
        assertThat(result.get(0).getOccurredAt()).isEqualTo(now.plusDays(1));
        assertThat(result.get(1).getOccurredAt()).isNull();
        assertThat(result.get(2).getOccurredAt()).isEqualTo(now.minusYears(1));
    }

    @Test
    @DisplayName("findDashboardEvents - broadcast 이벤트와 내 targeted 이벤트만 반환한다")
    void findDashboardEvents_filtersBroadcastAndTargetedEvents() {
        Workspace workspace = seedWorkspace("event-filter@test.com", "event-filter");
        Workspace otherWorkspace = seedWorkspace("event-filter-other@test.com", "event-filter-other");
        LocalDateTime now = LocalDateTime.of(2026, 6, 25, 12, 0);
        WorkspaceEvent broadcastEvent = saveEvent(workspace, WorkspaceEvent.EventType.PR_CREATED,
                1L, null, null, null, now);
        WorkspaceEvent broadcastPrReviewEvent = saveEvent(workspace, WorkspaceEvent.EventType.PR_REVIEW,
                3L, null, null, null, now.minusSeconds(30));
        WorkspaceEvent myReplyEvent = saveEvent(workspace, WorkspaceEvent.EventType.REPLY,
                null, null, 10L, 77L, now.minusMinutes(1));
        WorkspaceEvent myPrReviewEvent = saveEvent(workspace, WorkspaceEvent.EventType.PR_REVIEW,
                4L, null, null, 77L, now.minusMinutes(2));
        saveEvent(workspace, WorkspaceEvent.EventType.MENTION,
                null, null, 11L, 88L, now.minusMinutes(3));
        saveEvent(workspace, WorkspaceEvent.EventType.PR_REVIEW,
                5L, null, null, 88L, now.minusMinutes(4));
        saveEvent(otherWorkspace, WorkspaceEvent.EventType.ISSUE_CREATED,
                null, 2L, null, null, now.minusMinutes(5));
        em.flush();
        em.clear();

        List<WorkspaceEvent> result = workspaceEventRepository.findDashboardEvents(
                List.of(workspace.getId()),
                77L,
                List.of(WorkspaceEvent.EventType.PR_CREATED, WorkspaceEvent.EventType.ISSUE_CREATED, WorkspaceEvent.EventType.PR_REVIEW),
                List.of(WorkspaceEvent.EventType.PR_REVIEW, WorkspaceEvent.EventType.REPLY, WorkspaceEvent.EventType.MENTION),
                PageRequest.of(0, 10)
        );

        assertThat(result).extracting(WorkspaceEvent::getId)
                .containsExactlyInAnyOrder(
                        broadcastEvent.getId(),
                        broadcastPrReviewEvent.getId(),
                        myReplyEvent.getId(),
                        myPrReviewEvent.getId()
                );
        assertThat(result).extracting(WorkspaceEvent::getWorkspace)
                .allSatisfy(foundWorkspace -> assertThat(foundWorkspace.getId()).isEqualTo(workspace.getId()));
    }

    private Workspace seedWorkspace(String email, String slug) {
        User owner = userRepository.save(User.create(email, "hash", "Owner"));
        Workspace workspace = workspaceRepository.save(Workspace.create(owner, "Team", slug, null));
        em.flush();
        return workspace;
    }

    private WorkspaceEvent saveEvent(Workspace workspace, WorkspaceEvent.EventType type,
                                     Long prId, Long issueId, Long threadId, Long targetUserId,
                                     LocalDateTime occurredAt) {
        WorkspaceEvent event = WorkspaceEvent.create(
                workspace,
                type,
                "actor",
                prId,
                issueId,
                30L,
                "content",
                20L,
                "repo",
                threadId,
                prId != null ? 1L : null,
                issueId != null ? 2L : null,
                targetUserId,
                occurredAt
        );
        return workspaceEventRepository.saveAndFlush(event);
    }
}
