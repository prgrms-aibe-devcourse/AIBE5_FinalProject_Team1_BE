package com.team1.codedock.domain.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.document.dto.SwaggerSyncResponse;
import com.team1.codedock.domain.document.dto.SwaggerUrlRequest;
import com.team1.codedock.domain.document.service.ApiSpecSwaggerService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ApiSpecSwaggerControllerTest {

    @Mock
    private ApiSpecSwaggerService apiSpecSwaggerService;

    @InjectMocks
    private ApiSpecSwaggerController apiSpecSwaggerController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String SWAGGER_URL = "http://localhost:8080/v3/api-docs";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiSpecSwaggerController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
        objectMapper = new ObjectMapper();
    }

    private SwaggerSyncResponse sampleSyncResponse() {
        return new SwaggerSyncResponse(SWAGGER_URL, 3, 1);
    }

    // ── POST /api/workspaces/{workspaceId}/api-specs/swagger-url ──

    @Test
    @DisplayName("Swagger URL 등록 성공 시 200과 동기화 결과를 반환한다")
    void registerSwaggerUrl_성공_200() throws Exception {
        SwaggerUrlRequest request = new SwaggerUrlRequest(SWAGGER_URL);
        when(apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL)).thenReturn(sampleSyncResponse());

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.swaggerUrl").value(SWAGGER_URL))
                .andExpect(jsonPath("$.data.syncedCount").value(3));
    }

    @Test
    @DisplayName("swaggerUrl이 blank이면 400을 반환한다")
    void registerSwaggerUrl_blank_400() throws Exception {
        SwaggerUrlRequest request = new SwaggerUrlRequest("");

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/workspaces/{workspaceId}/api-specs/swagger-url ──

    @Test
    @DisplayName("Swagger URL 조회 성공 시 200과 URL을 반환한다")
    void getSwaggerUrl_성공_200() throws Exception {
        when(apiSpecSwaggerService.getSwaggerUrl(1L)).thenReturn(SWAGGER_URL);

        mockMvc.perform(get("/api/workspaces/1/api-specs/swagger-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(SWAGGER_URL));
    }

    @Test
    @DisplayName("Swagger URL이 등록되지 않았으면 404를 반환한다")
    void getSwaggerUrl_URL_없으면_404() throws Exception {
        when(apiSpecSwaggerService.getSwaggerUrl(1L))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED));

        mockMvc.perform(get("/api/workspaces/1/api-specs/swagger-url"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS002"));
    }

    // ── POST /api/workspaces/{workspaceId}/api-specs/swagger-url/resync ──

    @Test
    @DisplayName("재동기화 성공 시 200과 동기화 결과를 반환한다")
    void resync_성공_200() throws Exception {
        when(apiSpecSwaggerService.resync(1L)).thenReturn(sampleSyncResponse());

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url/resync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.completedChecklistCount").value(1));
    }

    @Test
    @DisplayName("Swagger URL이 등록되지 않은 상태에서 재동기화하면 404를 반환한다")
    void resync_URL_없으면_404() throws Exception {
        when(apiSpecSwaggerService.resync(1L))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_URL_NOT_REGISTERED));

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url/resync"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS002"));
    }

    @Test
    @DisplayName("Swagger URL 등록 시 데이터 fetch에 실패하면 502를 반환한다")
    void registerSwaggerUrl_fetch_실패_502() throws Exception {
        SwaggerUrlRequest request = new SwaggerUrlRequest(SWAGGER_URL);
        when(apiSpecSwaggerService.registerAndSync(1L, SWAGGER_URL))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_FETCH_ERROR));

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS003"));
    }

    @Test
    @DisplayName("재동기화 시 데이터 fetch에 실패하면 502를 반환한다")
    void resync_fetch_실패_502() throws Exception {
        when(apiSpecSwaggerService.resync(1L))
                .thenThrow(new BusinessException(ErrorCode.SWAGGER_FETCH_ERROR));

        mockMvc.perform(post("/api/workspaces/1/api-specs/swagger-url/resync"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS003"));
    }
}
