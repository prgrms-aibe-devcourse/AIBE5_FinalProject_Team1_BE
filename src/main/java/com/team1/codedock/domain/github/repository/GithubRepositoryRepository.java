package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    @Query(value = "SELECT * FROM github_repositories WHERE workspace_id = :workspaceId AND ROWNUM = 1", nativeQuery = true)
    Optional<GithubRepository> findFirstByWorkspace_Id(@Param("workspaceId") Long workspaceId);
}
