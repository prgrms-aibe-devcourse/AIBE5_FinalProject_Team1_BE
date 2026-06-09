package com.team1.codedock.domain.document.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.team1.codedock.domain.document.dto.ApiSpecCreateRequest;
import com.team1.codedock.domain.document.dto.ApiSpecResponse;
import com.team1.codedock.domain.document.dto.ApiSpecUpdateRequest;
import com.team1.codedock.domain.document.service.ApiSpecService;
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
class ApiSpecControllerTest {

    @Mock
    private ApiSpecService apiSpecService;

    @InjectMocks
    private ApiSpecController apiSpecController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(apiSpecController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private ApiSpecResponse sampleResponse() {
        return new ApiSpecResponse(
                1L, 1L, 1L,
                "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null,
                "design", null,
                null, null, null, null, null, null,
                null, "manual",
                null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
    }

    // ── POST /api/workspaces/{workspaceId}/api-specs ──────────

    @Test
    @DisplayName("API 명세 생성 성공 시 201과 생성된 명세를 반환한다")
    void createApiSpec_성공_201() throws Exception {
        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );
        when(apiSpecService.createApiSpec(eq(1L), any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/workspaces/1/api-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("회원 조회"))
                .andExpect(jsonPath("$.data.method").value("GET"));
    }

    @Test
    @DisplayName("title이 blank이면 400을 반환한다")
    void createApiSpec_title_blank_400() throws Exception {
        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(post("/api/workspaces/1/api-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("method가 blank이면 400을 반환한다")
    void createApiSpec_method_blank_400() throws Exception {
        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(post("/api/workspaces/1/api-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("endpoint가 blank이면 400을 반환한다")
    void createApiSpec_endpoint_blank_400() throws Exception {
        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                1L, "회원 조회", "GET", "",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(post("/api/workspaces/1/api-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("createdByMemberId가 null이면 400을 반환한다")
    void createApiSpec_memberId_null_400() throws Exception {
        ApiSpecCreateRequest request = new ApiSpecCreateRequest(
                null, "회원 조회", "GET", "/api/users/{id}",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(post("/api/workspaces/1/api-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ── GET /api/workspaces/{workspaceId}/api-specs ───────────

    @Test
    @DisplayName("API 명세 목록 조회 성공 시 200과 목록을 반환한다")
    void getApiSpecs_성공_200() throws Exception {
        when(apiSpecService.getApiSpecs(1L, null, null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/workspaces/1/api-specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].title").value("회원 조회"));
    }

    @Test
    @DisplayName("groupName 파라미터로 필터링된 목록을 조회한다")
    void getApiSpecs_groupName_필터_200() throws Exception {
        when(apiSpecService.getApiSpecs(1L, "User", null)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/workspaces/1/api-specs")
                        .param("groupName", "User"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("status 파라미터로 필터링된 목록을 조회한다")
    void getApiSpecs_status_필터_200() throws Exception {
        when(apiSpecService.getApiSpecs(1L, null, "design")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/workspaces/1/api-specs")
                        .param("status", "design"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("API 명세가 없으면 빈 배열을 반환한다")
    void getApiSpecs_빈_목록_200() throws Exception {
        when(apiSpecService.getApiSpecs(1L, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/1/api-specs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /api/workspaces/{workspaceId}/api-specs/{apiSpecId} ──

    @Test
    @DisplayName("API 명세 단건 조회 성공 시 200과 명세를 반환한다")
    void getApiSpec_성공_200() throws Exception {
        when(apiSpecService.getApiSpec(1L, 1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/workspaces/1/api-specs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.title").value("회원 조회"));
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 조회 시 404를 반환한다")
    void getApiSpec_없으면_404() throws Exception {
        when(apiSpecService.getApiSpec(1L, 99L))
                .thenThrow(new BusinessException(ErrorCode.API_SPEC_NOT_FOUND));

        mockMvc.perform(get("/api/workspaces/1/api-specs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS001"));
    }

    // ── PATCH /api/workspaces/{workspaceId}/api-specs/{apiSpecId} ──

    @Test
    @DisplayName("API 명세 수정 성공 시 200과 수정된 명세를 반환한다")
    void updateApiSpec_성공_200() throws Exception {
        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, "completed", null,
                null, null, null, null, null, 201,
                null, null, null, null, null
        );
        ApiSpecResponse updated = new ApiSpecResponse(
                1L, 1L, 1L,
                "수정된 제목", "POST", "/api/users",
                null, null, null, null,
                "completed", null,
                null, null, null, null, null, 201,
                null, "manual",
                null, null, null,
                LocalDateTime.now(), LocalDateTime.now()
        );
        when(apiSpecService.updateApiSpec(eq(1L), eq(1L), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/workspaces/1/api-specs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.title").value("수정된 제목"))
                .andExpect(jsonPath("$.data.status").value("completed"));
    }

    @Test
    @DisplayName("수정 시 title이 blank이면 400을 반환한다")
    void updateApiSpec_title_blank_400() throws Exception {
        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(patch("/api/workspaces/1/api-specs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정 시 method가 blank이면 400을 반환한다")
    void updateApiSpec_method_blank_400() throws Exception {
        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(patch("/api/workspaces/1/api-specs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("수정 시 endpoint가 blank이면 400을 반환한다")
    void updateApiSpec_endpoint_blank_400() throws Exception {
        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );

        mockMvc.perform(patch("/api/workspaces/1/api-specs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 수정 시 404를 반환한다")
    void updateApiSpec_없으면_404() throws Exception {
        ApiSpecUpdateRequest request = new ApiSpecUpdateRequest(
                "수정된 제목", "POST", "/api/users",
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                null, null, null, null, null
        );
        when(apiSpecService.updateApiSpec(eq(1L), eq(99L), any()))
                .thenThrow(new BusinessException(ErrorCode.API_SPEC_NOT_FOUND));

        mockMvc.perform(patch("/api/workspaces/1/api-specs/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS001"));
    }

    // ── DELETE /api/workspaces/{workspaceId}/api-specs/{apiSpecId} ──

    @Test
    @DisplayName("API 명세 삭제 성공 시 204를 반환한다")
    void deleteApiSpec_성공_204() throws Exception {
        mockMvc.perform(delete("/api/workspaces/1/api-specs/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("존재하지 않는 API 명세 삭제 시 404를 반환한다")
    void deleteApiSpec_없으면_404() throws Exception {
        doThrow(new BusinessException(ErrorCode.API_SPEC_NOT_FOUND))
                .when(apiSpecService).deleteApiSpec(1L, 99L);

        mockMvc.perform(delete("/api/workspaces/1/api-specs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AS001"));
    }
}
