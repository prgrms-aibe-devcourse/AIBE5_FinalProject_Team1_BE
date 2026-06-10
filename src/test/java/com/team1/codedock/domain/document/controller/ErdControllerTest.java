package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.ErdDocumentResponse;
import com.team1.codedock.domain.document.dto.ErdTableResponse;
import com.team1.codedock.domain.document.service.ErdService;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ErdControllerTest {

    @Mock
    private ErdService erdService;

    @InjectMocks
    private ErdController erdController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(erdController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private ErdDocumentResponse sampleErdDocumentResponse() {
        return new ErdDocumentResponse(
                1L, 1L, 1L,
                "ERD", null, "erDiagram\n...",
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/erd/generate ────────

    @Test
    @DisplayName("ERD 생성 성공 시 200과 생성된 ERD를 반환한다")
    void generateErd_성공_200() throws Exception {
        when(erdService.generateErd(1L)).thenReturn(sampleErdDocumentResponse());

        mockMvc.perform(post("/api/workspaces/1/erd/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("ERD"))
                .andExpect(jsonPath("$.data.mermaidCode").value("erDiagram\n..."));
    }

    @Test
    @DisplayName("ERD 생성 중 GitHub 레포가 없으면 404를 반환한다")
    void generateErd_GitHub_레포_없으면_404() throws Exception {
        when(erdService.generateErd(1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        mockMvc.perform(post("/api/workspaces/1/erd/generate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G001"));
    }

    // ── GET /api/workspaces/{workspaceId}/erd ──────────────────

    @Test
    @DisplayName("ERD 조회 성공 시 200과 ERD를 반환한다")
    void getErd_성공_200() throws Exception {
        when(erdService.getErd(1L)).thenReturn(sampleErdDocumentResponse());

        mockMvc.perform(get("/api/workspaces/1/erd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("ERD"))
                .andExpect(jsonPath("$.data.mermaidCode").value("erDiagram\n..."));
    }

    @Test
    @DisplayName("ERD가 없으면 404를 반환한다")
    void getErd_없으면_404() throws Exception {
        when(erdService.getErd(1L))
                .thenThrow(new BusinessException(ErrorCode.ERD_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/1/erd"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("E001"));
    }

    // ── GET /api/workspaces/{workspaceId}/erd/tables ───────────

    @Test
    @DisplayName("ERD 테이블 목록 조회 성공 시 200과 목록을 반환한다")
    void getErdTables_성공_200() throws Exception {
        ErdTableResponse tableResponse = new ErdTableResponse(1L, 1L, "users", "{}", "유저 테이블");
        when(erdService.getErdTables(1L)).thenReturn(List.of(tableResponse));

        mockMvc.perform(get("/api/workspaces/1/erd/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].tableName").value("users"));
    }

    @Test
    @DisplayName("ERD 테이블이 없으면 빈 배열을 반환한다")
    void getErdTables_빈_목록_200() throws Exception {
        when(erdService.getErdTables(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/1/erd/tables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
