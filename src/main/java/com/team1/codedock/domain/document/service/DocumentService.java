package com.team1.codedock.domain.document.service;

import com.team1.codedock.domain.document.dto.DocumentCreateRequest;
import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.dto.DocumentUpdateRequest;
import com.team1.codedock.domain.document.entity.Document;
import com.team1.codedock.domain.document.repository.DocumentRepository;
import com.team1.codedock.domain.pr.entity.GithubPullRequest;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import com.team1.codedock.domain.workspace.repository.WorkspaceMemberRepository;
import com.team1.codedock.domain.workspace.repository.WorkspaceRepository;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final EntityManager entityManager;

    @Transactional
    public DocumentResponse createDocument(Long workspaceId, DocumentCreateRequest request) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_NOT_FOUND));

        WorkspaceMember createdBy = workspaceMemberRepository.findById(request.createdByMemberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        GithubPullRequest relatedPr = null;
        if (request.relatedPrId() != null) {
            relatedPr = entityManager.find(GithubPullRequest.class, request.relatedPrId());
        }

        Document document = Document.create(
                workspace,
                createdBy,
                request.title(),
                request.content(),
                request.category(),
                request.visibility(),
                relatedPr
        );

        return DocumentResponse.from(documentRepository.save(document));
    }

    public List<DocumentResponse> getDocuments(Long workspaceId, String category) {
        List<Document> documents = (category != null && !category.isBlank())
                ? documentRepository.findAllByWorkspace_IdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId, category)
                : documentRepository.findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId);

        return documents.stream()
                .map(DocumentResponse::from)
                .toList();
    }

    public DocumentResponse getDocument(Long workspaceId, Long documentId) {
        Document document = documentRepository.findByIdAndWorkspace_IdAndDeletedAtIsNull(documentId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse updateDocument(Long workspaceId, Long documentId, DocumentUpdateRequest request) {
        Document document = documentRepository.findByIdAndWorkspace_IdAndDeletedAtIsNull(documentId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        document.update(request.title(), request.content(), request.visibility());
        return DocumentResponse.from(document);
    }

    @Transactional
    public void deleteDocument(Long workspaceId, Long documentId) {
        Document document = documentRepository.findByIdAndWorkspace_IdAndDeletedAtIsNull(documentId, workspaceId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        document.softDelete();
    }
}
