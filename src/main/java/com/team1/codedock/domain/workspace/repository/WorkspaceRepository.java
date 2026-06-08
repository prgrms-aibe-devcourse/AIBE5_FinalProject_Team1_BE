package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);
}