package com.team1.codedock.domain.document.repository;

import com.team1.codedock.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(Long workspaceId);

    List<Document> findAllByWorkspace_IdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(Long workspaceId, String category);

    Optional<Document> findByIdAndWorkspace_IdAndDeletedAtIsNull(Long id, Long workspaceId);
}
