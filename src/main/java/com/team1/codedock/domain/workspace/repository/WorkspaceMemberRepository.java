package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    Optional<WorkspaceMember> findByWorkspaceAndUser(Workspace workspace, User user);

    List<WorkspaceMember> findAllByWorkspace(Workspace workspace);

    List<WorkspaceMember> findAllByUser(User user);

    boolean existsByWorkspaceAndUser(Workspace workspace, User user);

    boolean existsByWorkspace_IdAndUser_IdAndIsActiveTrue(Long workspaceId, Long userId);

    int countByWorkspaceAndIsActiveTrue(Workspace workspace);
}
