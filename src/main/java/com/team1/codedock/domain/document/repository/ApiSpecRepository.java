package com.team1.codedock.domain.document.repository;

import com.team1.codedock.domain.document.entity.ApiSpec;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApiSpecRepository extends JpaRepository<ApiSpec, Long> {

    List<ApiSpec> findAllByWorkspace_IdOrderByCreatedAtDesc(Long workspaceId);

    List<ApiSpec> findAllByWorkspace_IdAndGroupNameOrderByCreatedAtDesc(Long workspaceId, String groupName);

    List<ApiSpec> findAllByWorkspace_IdAndStatusOrderByCreatedAtDesc(Long workspaceId, String status);

    Optional<ApiSpec> findByIdAndWorkspace_Id(Long id, Long workspaceId);

    @Modifying
    @Query("DELETE FROM ApiSpec a WHERE a.workspace.id = :workspaceId AND a.sourceType = :sourceType")
    void deleteAllByWorkspace_IdAndSourceType(@Param("workspaceId") Long workspaceId, @Param("sourceType") String sourceType);

    List<ApiSpec> findAllByWorkspace_IdAndSourceTypeNot(Long workspaceId, String sourceType);
}
