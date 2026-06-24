package com.team1.codedock.domain.dashboard.controller;

import com.team1.codedock.domain.dashboard.dto.DashboardEventResponse;
import com.team1.codedock.domain.dashboard.dto.DashboardSummaryResponse;
import com.team1.codedock.domain.dashboard.dto.WorkspaceDashboardResponse;
import com.team1.codedock.domain.dashboard.service.DashboardService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private DashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/dashboard/summary - 200과 요약 통계를 반환한다")
    void getSummary_success() throws Exception {
        DashboardSummaryResponse response = DashboardSummaryResponse.of(3L, 2L, 1L, 5L);
        when(dashboardService.getSummary(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.openIssueCount").value(3))
                .andExpect(jsonPath("$.data.openPrCount").value(2))
                .andExpect(jsonPath("$.data.reviewRequestCount").value(1))
                .andExpect(jsonPath("$.data.receivedReviewCount").value(5));
    }

    @Test
    @DisplayName("GET /api/dashboard/workspaces - 200과 워크스페이스 통계 목록을 반환한다")
    void getWorkspaceStats_success() throws Exception {
        List<WorkspaceDashboardResponse> response = List.of(
                new WorkspaceDashboardResponse(10L, "프로젝트", null, 2L, 3L, 1L, 4L)
        );
        when(dashboardService.getWorkspaceStats(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].workspaceId").value(10))
                .andExpect(jsonPath("$.data[0].workspaceName").value("프로젝트"))
                .andExpect(jsonPath("$.data[0].openIssueCount").value(2))
                .andExpect(jsonPath("$.data[0].openPrCount").value(3));
    }

    @Test
    @DisplayName("GET /api/dashboard/events - 주요 이벤트 시간과 이동 타입을 반환한다")
    void getEvents_success() throws Exception {
        List<DashboardEventResponse> response = List.of(
                new DashboardEventResponse(
                        1L, "PR_CREATED", 10L, "프로젝트", "alice",
                        5L, null, null, 7L, "my-repo", null, 1L, null,
                        "PR 생성", false,
                        LocalDateTime.of(2026, 6, 23, 10, 0),
                        LocalDateTime.of(2026, 6, 22, 9, 30),
                        "PR"
                )
        );
        when(dashboardService.getEvents(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].eventId").value(1))
                .andExpect(jsonPath("$.data[0].type").value("PR_CREATED"))
                .andExpect(jsonPath("$.data[0].actorName").value("alice"))
                .andExpect(jsonPath("$.data[0].occurredAt[0]").value(2026))
                .andExpect(jsonPath("$.data[0].occurredAt[1]").value(6))
                .andExpect(jsonPath("$.data[0].occurredAt[2]").value(22))
                .andExpect(jsonPath("$.data[0].occurredAt[3]").value(9))
                .andExpect(jsonPath("$.data[0].occurredAt[4]").value(30))
                .andExpect(jsonPath("$.data[0].navigationType").value("PR"))
                .andExpect(jsonPath("$.data[0].isRead").value(false));
    }

    @Test
    @DisplayName("PATCH /api/dashboard/events/{eventId}/read - 서비스에 읽음 처리를 위임한다")
    void markEventAsRead_success() throws Exception {
        mockMvc.perform(patch("/api/dashboard/events/200/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(dashboardService).markEventAsRead(200L, USER_ID);
    }

    @Test
    @DisplayName("PATCH /api/dashboard/events/{eventId}/read - 권한이 없으면 403을 반환한다")
    void markEventAsRead_forbidden() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(dashboardService).markEventAsRead(200L, USER_ID);

        mockMvc.perform(patch("/api/dashboard/events/200/read"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
