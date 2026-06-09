package com.team1.codedock.domain.document.repository;

import com.team1.codedock.domain.document.entity.ApiSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApiSpecRepository extends JpaRepository<ApiSpec, Long> {

    List<ApiSpec> findAllByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId);

    List<ApiSpec> findAllByWorkspace_IdAndGroupNameOrderByCreatedAtDesc(Long workspaceId, String groupName);

    List<ApiSpec> findAllByWorkspace_IdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    Optional<ApiSpec> findByIdAndWorkspace_Id(Long id, Long workspaceId);
}
