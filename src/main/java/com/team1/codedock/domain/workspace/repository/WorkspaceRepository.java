package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    Optional<Workspace> findBySlug(String slug);

    // Oracle 11g는 FETCH FIRST 구문 미지원 → COUNT로 대체
    @Query("SELECT COUNT(w) FROM Workspace w WHERE w.slug = :slug")
    long countBySlug(@Param("slug") String slug);
}