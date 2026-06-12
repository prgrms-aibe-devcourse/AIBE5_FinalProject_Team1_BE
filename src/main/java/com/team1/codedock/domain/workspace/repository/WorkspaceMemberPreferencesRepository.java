package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.entity.WorkspaceMemberPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkspaceMemberPreferencesRepository extends JpaRepository<WorkspaceMemberPreferences, Long> {

    Optional<WorkspaceMemberPreferences> findByWorkspaceMember(WorkspaceMember member);
}
