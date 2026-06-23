package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, User user);

    List<WorkspaceMember> findAllByWorkspace(Workspace workspace);

    List<WorkspaceMember> findAllByUser(User user);

    // Oracle 11g는 FETCH FIRST 구문 미지원 → COUNT로 대체
    @Query("SELECT COUNT(m) FROM WorkspaceMember m WHERE m.workspace = :workspace AND m.user = :user")
    long countByWorkspaceAndUser(@Param("workspace") Workspace workspace, @Param("user") User user);

    @Query("SELECT COUNT(m) FROM WorkspaceMember m WHERE m.workspace.id = :workspaceId AND m.user.id = :userId AND m.isActive = true")
    long countByWorkspace_IdAndUser_IdAndIsActiveTrue(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM WorkspaceMember m WHERE m.workspace.id = :workspaceId AND m.isActive = true AND LOWER(m.user.email) = LOWER(:email)")
    long countActiveByWorkspaceIdAndUserEmail(@Param("workspaceId") Long workspaceId, @Param("email") String email);

    Optional<WorkspaceMember> findByWorkspace_IdAndUser_IdAndIsActiveTrue(Long workspaceId, Long userId);

    List<WorkspaceMember> findAllByWorkspace_IdAndIsActiveTrue(Long workspaceId);

    Optional<WorkspaceMember> findByIdAndWorkspace_Id(Long id, Long workspaceId);

    // 멘션 토큰과 매칭되는 활성 워크스페이스 멤버 조회함
    @Query("""
            SELECT m
            FROM WorkspaceMember m
            JOIN FETCH m.user u
            WHERE m.workspace.id = :workspaceId
              AND m.isActive = true
              AND (
                  LOWER(u.username) IN :mentionNames
                  OR LOWER(u.githubUsername) IN :mentionNames
                  OR LOWER(u.nickname) IN :mentionNames
                  OR LOWER(u.displayName) IN :mentionNames
              )
            """)
    List<WorkspaceMember> findActiveMentionTargets(
            @Param("workspaceId") Long workspaceId,
            @Param("mentionNames") List<String> mentionNames
    );

    int countByWorkspaceAndIsActiveTrue(Workspace workspace);

    void deleteAllByWorkspace(Workspace workspace);

    List<WorkspaceMember> findAllByUser_IdAndIsActiveTrue(Long userId);
}
