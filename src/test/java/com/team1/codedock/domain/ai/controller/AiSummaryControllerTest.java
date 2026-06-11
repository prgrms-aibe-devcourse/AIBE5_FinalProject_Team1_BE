package com.team1.codedock.domain.ai.controller;

import com.team1.codedock.domain.ai.dto.AiSummaryResponse;
import com.team1.codedock.domain.ai.service.AiSummaryService;
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
class AiSummaryControllerTest {

    @Mock
    private AiSummaryService aiSummaryService;

    @InjectMocks
    private AiSummaryController aiSummaryController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(aiSummaryController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private AiSummaryResponse sampleResponse() {
        return new AiSummaryResponse(
                1L, 1L, "completed", "High",
                "PR 요약", List.of("주의사항"), List.of("긍정적인 점"), null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/pull-requests/{prId}/ai-summary ──

    @Test
    @DisplayName("AI 요약 생성 성공 시 200과 생성된 요약을 반환한다")
    void generateSummary_성공_200() throws Exception {
        when(aiSummaryService.generateSummary(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.riskLevel").value("High"));
    }

    @Test
    @DisplayName("워크스페이스 멤버가 아니면 404를 반환한다")
    void generateSummary_멤버_없으면_404() throws Exception {
        when(aiSummaryService.generateSummary(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.WORKSPACE_MEMBER_NOT_FOUND));

        mockMvc.perform(post("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("W002"));
    }

    @Test
    @DisplayName("PR을 찾을 수 없으면 404를 반환한다")
    void generateSummary_PR_없으면_404() throws Exception {
        when(aiSummaryService.generateSummary(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        mockMvc.perform(post("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G003"));
    }

    @Test
    @DisplayName("AI 분석에 실패하면 502를 반환한다")
    void generateSummary_AI_분석_실패_502() throws Exception {
        when(aiSummaryService.generateSummary(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.AI_ANALYSIS_FAILED));

        mockMvc.perform(post("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI002"));
    }

    // ── GET /api/workspaces/{workspaceId}/pull-requests/{prId}/ai-summary ──

    @Test
    @DisplayName("AI 요약 조회 성공 시 200과 요약을 반환한다")
    void getSummary_성공_200() throws Exception {
        when(aiSummaryService.getSummary(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("completed"))
                .andExpect(jsonPath("$.data.riskLevel").value("High"));
    }

    @Test
    @DisplayName("AI 요약을 찾을 수 없으면 404를 반환한다")
    void getSummary_요약_없으면_404() throws Exception {
        when(aiSummaryService.getSummary(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.AI_SUMMARY_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AI001"));
    }

    @Test
    @DisplayName("PR을 찾을 수 없으면 404를 반환한다")
    void getSummary_PR_없으면_404() throws Exception {
        when(aiSummaryService.getSummary(1L, 1L))
                .thenThrow(new BusinessException(ErrorCode.GITHUB_PR_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/1/pull-requests/1/ai-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("G003"));
    }
}
