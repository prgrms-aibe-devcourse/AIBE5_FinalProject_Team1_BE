package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.DocumentResponse;
import com.team1.codedock.domain.document.service.DocumentAiService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DocumentAiControllerTest {

    @Mock
    private DocumentAiService documentAiService;

    @InjectMocks
    private DocumentAiController documentAiController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(documentAiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private DocumentResponse sampleResponse() {
        return new DocumentResponse(
                1L, 1L, 1L,
                "AI 문서 제목", "AI 문서 내용", "manual", "AI", "workspace",
                null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/documents/ai-generate ──

    @Test
    @DisplayName("AI 문서 생성 성공 시 200과 생성된 문서를 반환한다")
    void generateDocument_성공_200() throws Exception {
        when(documentAiService.generateDocument(1L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("AI 문서 제목"))
                .andExpect(jsonPath("$.data.generatedBy").value("AI"));
    }

    @Test
    @DisplayName("GitHub 레포가 없으면 404를 반환한다")
    void generateDocument_GitHub_레포_없으면_404() throws Exception {
        when(documentAiService.generateDocument(1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G001"));
    }
}
