package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.Invitation;
import com.team1.codedock.domain.workspace.entity.Workspace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.List;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    List<Invitation> findAllByWorkspace(Workspace workspace);
    Optional<Invitation> findByIdAndWorkspace_Id(Long id, Long workspaceId);
    Optional<Invitation> findByToken(String token);
    List<Invitation> findAllByInvitedEmailIgnoreCaseAndStatus(String invitedEmail, String status);

    @Query("SELECT i FROM Invitation i WHERE i.status = :status AND LOWER(i.invitedEmail) IN (:emails)")
    List<Invitation> findAllByStatusAndLoweredInvitedEmailIn(@Param("status") String status, @Param("emails") Collection<String> loweredEmails);

    void deleteAllByWorkspace(Workspace workspace);

    boolean existsByWorkspace_IdAndInvitedEmailIgnoreCaseAndStatus(Long workspaceId, String invitedEmail, String status);
}