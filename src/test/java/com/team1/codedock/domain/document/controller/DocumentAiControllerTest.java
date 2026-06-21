package com.team1.codedock.domain.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

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
        when(documentAiService.generateDocument(eq(1L), any())).thenReturn(sampleResponse());
        String body = objectMapper.writeValueAsString(Map.of("category", "manual", "topic", "user"));

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("AI 문서 제목"))
                .andExpect(jsonPath("$.data.generatedBy").value("AI"));
    }

    @Test
    @DisplayName("GitHub 레포가 없으면 404를 반환한다")
    void generateDocument_GitHub_레포_없으면_404() throws Exception {
        when(documentAiService.generateDocument(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_REPO_NOT_FOUND));
        String body = objectMapper.writeValueAsString(Map.of("category", "manual", "topic", "user"));

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G001"));
    }

    @Test
    @DisplayName("category가 없으면 400을 반환한다")
    void generateDocument_category_없으면_400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("topic", "user"));

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("topic 없는 manual 요청 시 서비스에서 400을 반환한다")
    void generateDocument_topic_없는_manual_400() throws Exception {
        when(documentAiService.generateDocument(eq(1L), any()))
                .thenThrow(new BusinessException(ErrorCode.TOPIC_REQUIRED));
        String body = objectMapper.writeValueAsString(Map.of("category", "manual"));

        mockMvc.perform(post("/api/workspaces/1/documents/ai-generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI004"));
    }
}
