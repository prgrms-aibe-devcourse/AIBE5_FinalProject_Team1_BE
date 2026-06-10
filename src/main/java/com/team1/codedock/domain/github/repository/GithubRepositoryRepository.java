package com.team1.codedock.domain.github.repository;

import com.team1.codedock.domain.github.entity.GithubRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GithubRepositoryRepository extends JpaRepository<GithubRepository, Long> {

    Optional<GithubRepository> findFirstByWorkspace_Id(Long workspaceId);
}
