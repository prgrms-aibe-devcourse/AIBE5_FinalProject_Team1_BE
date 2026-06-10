package com.team1.codedock.domain.document.repository;

import com.team1.codedock.domain.document.entity.ErdDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ErdDocumentRepository extends JpaRepository<ErdDocument, Long> {

    Optional<ErdDocument> findByWorkspace_IdAndDeletedAtIsNull(Long workspaceId);
}
