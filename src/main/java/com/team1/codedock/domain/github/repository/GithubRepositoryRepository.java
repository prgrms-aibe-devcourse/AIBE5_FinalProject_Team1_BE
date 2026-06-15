package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    @Query("SELECT g FROM GithubRepository g WHERE g.workspace.id = :workspaceId")
    List<GithubRepository> findByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
