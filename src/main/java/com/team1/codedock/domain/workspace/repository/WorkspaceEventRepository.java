package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.WorkspaceEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceEventRepository extends JpaRepository<WorkspaceEvent, Long> {

    List<WorkspaceEvent> findAllByWorkspace_IdInOrderByCreatedAtDesc(List<Long> workspaceIds);
}
