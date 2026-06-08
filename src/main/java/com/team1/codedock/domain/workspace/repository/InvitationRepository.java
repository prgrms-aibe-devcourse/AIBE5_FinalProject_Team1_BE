package com.team1.codedock.domain.workspace.repository;

import com.team1.codedock.domain.workspace.entity.Invitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);
}