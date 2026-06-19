package com.team1.codedock.domain.workspace.controller;

import com.team1.codedock.domain.workspace.dto.WorkspaceEventResponse;
import com.team1.codedock.domain.workspace.service.WorkspaceEventService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WorkspaceEventControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private WorkspaceEventService workspaceEventService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(USER_ID);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        mockMvc = MockMvcBuilders.standaloneSetup(new WorkspaceEventController(workspaceEventService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("내 이벤트 목록을 반환한다")
    void getMyEvents_성공() throws Exception {
        WorkspaceEventResponse event = new WorkspaceEventResponse(
                100L, 10L, "MENTION", "actor", null, null, 1L, "hello",
                LocalDateTime.of(2026, 6, 18, 12, 0), null, null, null, null, null);
        when(workspaceEventService.getEventsForUser(USER_ID)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(100))
                .andExpect(jsonPath("$.data[0].workspaceId").value(10))
                .andExpect(jsonPath("$.data[0].type").value("MENTION"))
                .andExpect(jsonPath("$.data[0].actorName").value("actor"))
                .andExpect(jsonPath("$.data[0].channelId").value(1))
                .andExpect(jsonPath("$.data[0].content").value("hello"));
    }

    @Test
    @DisplayName("사용자가 없으면 404를 반환한다")
    void getMyEvents_유저없음() throws Exception {
        when(workspaceEventService.getEventsForUser(USER_ID))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/v1/events"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
