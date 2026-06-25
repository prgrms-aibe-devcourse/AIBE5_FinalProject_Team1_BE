package com.team1.codedock.domain.issue.controller;

import com.team1.codedock.domain.issue.dto.IssueLocalStatusUpdateRequest;
import com.team1.codedock.domain.issue.dto.IssueResponse;
import com.team1.codedock.domain.issue.service.IssueService;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IssueControllerTest {

    private static final Long USER_ID = 100L;

    @Mock
    private IssueService issueService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new IssueController(issueService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("작업보드 목록 조회는 이슈 state와 localStatus를 그대로 반환한다")
    void getIssues_returnsStateAndLocalStatus() throws Exception {
        when(issueService.getWorkspaceIssues(10L, USER_ID)).thenReturn(List.of(
                issueResponse(40L, "open", "todo"),
                issueResponse(41L, "closed", "done")
        ));

        mockMvc.perform(get("/api/workspaces/10/issues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(40))
                .andExpect(jsonPath("$.data[0].state").value("open"))
                .andExpect(jsonPath("$.data[0].localStatus").value("todo"))
                .andExpect(jsonPath("$.data[1].id").value(41))
                .andExpect(jsonPath("$.data[1].state").value("closed"))
                .andExpect(jsonPath("$.data[1].localStatus").value("done"));

        verify(issueService).getWorkspaceIssues(10L, USER_ID);
    }

    @Test
    @DisplayName("작업보드 상태 변경은 JWT 사용자 기준으로 서비스에 위임한다")
    void updateLocalStatus_success() throws Exception {
        when(issueService.updateLocalStatus(any(), any(), any(), any()))
                .thenReturn(issueResponse(40L, "open", "review"));

        mockMvc.perform(patch("/api/workspaces/10/issues/40/local-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localStatus\":\"review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(40))
                .andExpect(jsonPath("$.data.state").value("open"))
                .andExpect(jsonPath("$.data.localStatus").value("review"));

        ArgumentCaptor<IssueLocalStatusUpdateRequest> requestCaptor =
                ArgumentCaptor.forClass(IssueLocalStatusUpdateRequest.class);
        verify(issueService).updateLocalStatus(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(40L),
                org.mockito.ArgumentMatchers.eq(USER_ID),
                requestCaptor.capture()
        );
        assertThat(requestCaptor.getValue().localStatus()).isEqualTo("review");
    }

    @Test
    @DisplayName("허용되지 않은 작업보드 상태 요청은 서비스 호출 전에 400으로 거부한다")
    void updateLocalStatus_invalidPayload() throws Exception {
        mockMvc.perform(patch("/api/workspaces/10/issues/40/local-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localStatus\":\"finished\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()));

        verifyNoInteractions(issueService);
    }

    @Test
    @DisplayName("닫힌 이슈를 todo로 되돌리는 요청은 INVALID_INPUT 응답으로 변환된다")
    void updateLocalStatus_closedIssueInvalidInput() throws Exception {
        when(issueService.updateLocalStatus(any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "GitHub에서 닫힌 이슈는 완료 상태로만 유지할 수 있습니다."
                ));

        mockMvc.perform(patch("/api/v1/workspaces/10/issues/40/local-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"localStatus\":\"todo\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT.getCode()))
                .andExpect(jsonPath("$.message").value("GitHub에서 닫힌 이슈는 완료 상태로만 유지할 수 있습니다."));
    }

    private static IssueResponse issueResponse(Long id, String state, String localStatus) {
        return new IssueResponse(
                id,
                "9001",
                30L,
                "team/repo",
                20L,
                7,
                "이슈",
                "설명",
                state,
                localStatus,
                "https://github.com/team/repo/issues/7",
                "octocat",
                "high",
                "bug",
                List.of(),
                List.of(),
                null,
                "2026-06-24T10:00:00Z",
                "2026-06-25T11:00:00Z",
                LocalDateTime.of(2026, 6, 25, 12, 0)
        );
    }
}
