package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findAllByWorkspace(Workspace workspace);
    Optional<Invitation> findByIdAndWorkspace_Id(Long id, Long workspaceId);
    Optional<Invitation> findByToken(String token);
    List<Invitation> findAllByInvitedEmailIgnoreCaseAndStatus(String invitedEmail, String status);

    void deleteAllByWorkspace(Workspace workspace);
}