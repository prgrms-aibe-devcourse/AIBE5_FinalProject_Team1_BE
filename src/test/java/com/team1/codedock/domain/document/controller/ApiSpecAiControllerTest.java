package com.team1.codedock.domain.document.controller;

import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.service.ApiSpecAiService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiSpecAiControllerTest {

    @Mock
    private ApiSpecAiService apiSpecAiService;

    @InjectMocks
    private ApiSpecAiController apiSpecAiController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiSpecAiController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    private ApiSpecResponse sampleAiResponse() {
        return new ApiSpecResponse(
                1L, 1L, 1L,
                "누락 API", "GET", "/api/items",
                "Item", null, "아이템 조회", null,
                "design", null,
                null, null, null, null, null, null,
                null, "AI",
                null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/api-specs/ai-checklist ──

    @Test
    @DisplayName("AI 체크리스트 생성 성공 시 200과 생성된 목록을 반환한다")
    void generateChecklist_성공_200() throws Exception {
        when(apiSpecAiService.generateChecklist(1L)).thenReturn(List.of(sampleAiResponse()));

        mockMvc.perform(post("/api/workspaces/1/api-specs/ai-checklist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].sourceType").value("AI"))
                .andExpect(jsonPath("$.data[0].status").value("design"));
    }

    @Test
    @DisplayName("Swagger URL이 등록되지 않았으면 404를 반환한다")
    void generateChecklist_swagger_URL_없으면_404() throws Exception {
        when(apiSpecAiService.generateChecklist(1L))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED));

        mockMvc.perform(post("/api/workspaces/1/api-specs/ai-checklist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS002"));
    }

    @Test
    @DisplayName("Swagger URL에서 데이터 fetch에 실패하면 502를 반환한다")
    void generateChecklist_fetch_실패_502() throws Exception {
        when(apiSpecAiService.generateChecklist(1L))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_FETCH_ERROR));

        mockMvc.perform(post("/api/workspaces/1/api-specs/ai-checklist"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS003"));
    }
}
