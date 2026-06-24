package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.WorkspaceEventReadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Set;

public interface WorkspaceEventReadStatusRepository extends JpaRepository<WorkspaceEventReadStatus, Long> {

    boolean existsByWorkspaceEventIdAndUserId(Long workspaceEventId, Long userId);

    @Query("SELECT r.workspaceEventId FROM WorkspaceEventReadStatus r WHERE r.userId = :userId AND r.workspaceEventId IN :eventIds")
    Set<Long> findReadEventIdsByUserIdAndEventIds(@Param("userId") Long userId, @Param("eventIds") Collection<Long> eventIds);
}
