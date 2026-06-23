package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceEventRepository extends JpaRepository<WorkspaceEvent, Long> {

    List<WorkspaceEvent> findAllByWorkspace_IdInOrderByCreatedAtDesc(List<Long> workspaceIds);

    @Query("""
            SELECT e FROM WorkspaceEvent e
            JOIN FETCH e.workspace w
            WHERE (
                e.type IN ('PR_CREATED', 'ISSUE_CREATED') AND e.workspace.id IN :workspaceIds
            ) OR (
                e.type IN ('PR_REVIEW', 'REPLY', 'MENTION') AND e.targetUserId = :userId
            )
            ORDER BY e.createdAt DESC
            """)
    List<WorkspaceEvent> findDashboardEvents(
            @Param("workspaceIds") List<Long> workspaceIds,
            @Param("userId") Long userId
    );

    @Query("SELECT e FROM WorkspaceEvent e JOIN FETCH e.workspace WHERE e.id = :id")
    Optional<WorkspaceEvent> findByIdWithWorkspace(@Param("id") Long id);
}
