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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository workspaceMemberRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private DocumentService documentService;

    // ── createDocument() ──────────────────────────────────────

    @Test
    @DisplayName("relatedPrId 없이 문서를 정상 생성한다")
    void createDocument_성공_relatedPr_없음() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        DocumentCreateRequest request = new DocumentCreateRequest(1L, "제목", "내용", "manual", "workspace", null);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findById(1L)).thenReturn(Optional.of(member));

        Document document = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        DocumentResponse response = documentService.createDocument(1L, request);

        assertThat(response.title()).isEqualTo("제목");
        assertThat(response.generatedBy()).isEqualTo("Manual");
        verify(entityManager, never()).find(any(), any());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    @DisplayName("relatedPrId 있으면 EntityManager로 PR을 조회한다")
    void createDocument_성공_relatedPr_있음() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        GithubPullRequest pr = mock(GithubPullRequest.class);
        DocumentCreateRequest request = new DocumentCreateRequest(1L, "제목", "내용", "pr-summary", "workspace", 10L);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(entityManager.find(GithubPullRequest.class, 10L)).thenReturn(pr);

        Document document = Document.create(workspace, member, "제목", "내용", "pr-summary", "workspace", pr);
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        documentService.createDocument(1L, request);

        verify(entityManager).find(GithubPullRequest.class, 10L);
    }

    @Test
    @DisplayName("존재하지 않는 워크스페이스로 문서 생성 시 예외가 발생한다")
    void createDocument_워크스페이스_없으면_예외() {
        DocumentCreateRequest request = new DocumentCreateRequest(1L, "제목", "내용", "manual", "workspace", null);
        when(workspaceRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.createDocument(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 멤버로 문서 생성 시 예외가 발생한다")
    void createDocument_멤버_없으면_예외() {
        Workspace workspace = mock(Workspace.class);
        DocumentCreateRequest request = new DocumentCreateRequest(99L, "제목", "내용", "manual", "workspace", null);

        when(workspaceRepository.findById(1L)).thenReturn(Optional.of(workspace));
        when(workspaceMemberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.createDocument(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND.getMessage());

        verify(documentRepository, never()).save(any());
    }

    // ── getDocuments() ────────────────────────────────────────

    @Test
    @DisplayName("category 없이 조회 시 전체 문서 목록을 반환한다")
    void getDocuments_category_없으면_전체_조회() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        Document doc = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);

        when(documentRepository.findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocuments(1L, null);

        assertThat(result).hasSize(1);
        verify(documentRepository).findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(1L);
        verify(documentRepository, never())
                .findAllByWorkspace_IdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(any(), any());
    }

    @Test
    @DisplayName("category 있으면 필터링된 문서 목록을 반환한다")
    void getDocuments_category_있으면_필터_조회() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        Document doc = Document.create(workspace, member, "릴리즈", "내용", "release", "workspace", null);

        when(documentRepository.findAllByWorkspace_IdAndCategoryAndDeletedAtIsNullOrderByCreatedAtDesc(1L, "release"))
                .thenReturn(List.of(doc));

        List<DocumentResponse> result = documentService.getDocuments(1L, "release");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).category()).isEqualTo("release");
        verify(documentRepository, never())
                .findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(any());
    }

    @Test
    @DisplayName("문서가 없으면 빈 리스트를 반환한다")
    void getDocuments_빈_리스트_반환() {
        when(documentRepository.findAllByWorkspace_IdAndDeletedAtIsNullOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        List<DocumentResponse> result = documentService.getDocuments(1L, null);

        assertThat(result).isEmpty();
    }

    // ── getDocument() ─────────────────────────────────────────

    @Test
    @DisplayName("문서 단건을 정상적으로 조회한다")
    void getDocument_성공() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        Document doc = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);

        when(documentRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(doc));

        DocumentResponse response = documentService.getDocument(1L, 1L);

        assertThat(response.title()).isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 문서 조회 시 예외가 발생한다")
    void getDocument_없으면_예외() {
        when(documentRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    // ── updateDocument() ──────────────────────────────────────

    @Test
    @DisplayName("문서를 정상적으로 수정한다")
    void updateDocument_성공() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        Document doc = Document.create(workspace, member, "원래 제목", "원래 내용", "manual", "workspace", null);

        when(documentRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(doc));

        DocumentUpdateRequest request = new DocumentUpdateRequest("수정된 제목", "수정된 내용", "public");
        DocumentResponse response = documentService.updateDocument(1L, 1L, request);

        assertThat(response.title()).isEqualTo("수정된 제목");
        assertThat(response.content()).isEqualTo("수정된 내용");
        assertThat(response.visibility()).isEqualTo("public");
    }

    @Test
    @DisplayName("존재하지 않는 문서 수정 시 예외가 발생한다")
    void updateDocument_없으면_예외() {
        when(documentRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        DocumentUpdateRequest request = new DocumentUpdateRequest("수정된 제목", "수정된 내용", "public");

        assertThatThrownBy(() -> documentService.updateDocument(1L, 99L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }

    // ── deleteDocument() ──────────────────────────────────────

    @Test
    @DisplayName("문서를 soft delete로 정상 삭제한다")
    void deleteDocument_성공() {
        Workspace workspace = mock(Workspace.class);
        WorkspaceMember member = mock(WorkspaceMember.class);
        Document doc = Document.create(workspace, member, "제목", "내용", "manual", "workspace", null);

        when(documentRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(doc));

        documentService.deleteDocument(1L, 1L);

        assertThat(doc.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 문서 삭제 시 예외가 발생한다")
    void deleteDocument_없으면_예외() {
        when(documentRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(ErrorCode.DOCUMENT_NOT_FOUND.getMessage());
    }
}
