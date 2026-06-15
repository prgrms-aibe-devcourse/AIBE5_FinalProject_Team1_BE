package com.team1.codedock.domain.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team1.codedock.domain.document.dto.DocumentCreateRequest;
import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.dto.DocumentUpdateRequest;
import com.team1.codedock.domain.document.service.DocumentService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController documentController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private DocumentResponse sampleResponse() {
        return new DocumentResponse(
                1L, 1L, 1L,
                "테스트 문서", "내용", "manual", "Manual", "workspace",
                null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/documents ──────────

    @Test
    @DisplayName("문서 생성 성공 시 201과 생성된 문서를 반환한다")
    void createDocument_성공_201() throws Exception {
        DocumentCreateRequest request = new DocumentCreateRequest(1L, "테스트 문서", "내용", "manual", "workspace", null);
        when(documentService.createDocument(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/workspaces/1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("테스트 문서"))
                .andExpect(jsonPath("$.data.generatedBy").value("Manual"));
    }

    @Test
    @DisplayName("제목이 blank이면 400을 반환한다")
    void createDocument_제목_blank_400() throws Exception {
        DocumentCreateRequest request = new DocumentCreateRequest(1L, "", "내용", "manual", "workspace", null);

        mockMvc.perform(post("/api/workspaces/1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("createdByMemberId가 null이면 400을 반환한다")
    void createDocument_memberId_null_400() throws Exception {
        DocumentCreateRequest request = new DocumentCreateRequest(null, "제목", "내용", "manual", "workspace", null);

        mockMvc.perform(post("/api/workspaces/1/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/workspaces/{workspaceId}/documents ───────────

    @Test
    @DisplayName("문서 목록 조회 성공 시 200과 목록을 반환한다")
    void getDocuments_성공_200() throws Exception {
        when(documentService.getDocuments(1L, null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/workspaces/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("테스트 문서"));
    }

    @Test
    @DisplayName("category 파라미터로 필터링된 목록을 조회한다")
    void getDocuments_category_필터_200() throws Exception {
        when(documentService.getDocuments(1L, "release")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/workspaces/1/documents")
                        .param("category", "release"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("문서가 없으면 빈 배열을 반환한다")
    void getDocuments_빈_목록_200() throws Exception {
        when(documentService.getDocuments(1L, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/1/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /api/workspaces/{workspaceId}/documents/{documentId} ──

    @Test
    @DisplayName("문서 단건 조회 성공 시 200과 문서를 반환한다")
    void getDocument_성공_200() throws Exception {
        when(documentService.getDocument(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/workspaces/1/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("테스트 문서"));
    }

    @Test
    @DisplayName("존재하지 않는 문서 조회 시 404를 반환한다")
    void getDocument_없으면_404() throws Exception {
        when(documentService.getDocument(1L, 99L))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/1/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("D001"));
    }

    // ── PATCH /api/workspaces/{workspaceId}/documents/{documentId} ──

    @Test
    @DisplayName("문서 수정 성공 시 200과 수정된 문서를 반환한다")
    void updateDocument_성공_200() throws Exception {
        DocumentUpdateRequest request = new DocumentUpdateRequest("수정된 제목", "수정된 내용", "public", "faq");
        DocumentResponse updated = new DocumentResponse(
                1L, 1L, 1L,
                "수정된 제목", "수정된 내용", "faq", "Manual", "public",
                null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(documentService.updateDocument(eq(1L), eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/workspaces/1/documents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.visibility").value("public"))
                .andExpect(jsonPath("$.data.category").value("faq"));
    }

    @Test
    @DisplayName("존재하지 않는 문서 수정 시 404를 반환한다")
    void updateDocument_없으면_404() throws Exception {
        DocumentUpdateRequest request = new DocumentUpdateRequest("수정된 제목", "수정된 내용", "public", null);
        when(documentService.updateDocument(eq(1L), eq(99L), any()))
                .thenThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        mockMvc.perform(patch("/api/workspaces/1/documents/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("D001"));
    }

    @Test
    @DisplayName("수정 시 제목이 blank이면 400을 반환한다")
    void updateDocument_제목_blank_400() throws Exception {
        DocumentUpdateRequest request = new DocumentUpdateRequest("", "수정된 내용", "public", null);

        mockMvc.perform(patch("/api/workspaces/1/documents/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── DELETE /api/workspaces/{workspaceId}/documents/{documentId} ──

    @Test
    @DisplayName("문서 삭제 성공 시 204를 반환한다")
    void deleteDocument_성공_204() throws Exception {
        mockMvc.perform(delete("/api/workspaces/1/documents/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 문서 삭제 시 404를 반환한다")
    void deleteDocument_없으면_404() throws Exception {
        doThrow(new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND))
                .when(documentService).deleteDocument(1L, 99L);

        mockMvc.perform(delete("/api/workspaces/1/documents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("D001"));
    }
}
