package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.service.MentionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MentionControllerTest {

    private static final Long USER_ID = 30L;

    @Mock
    private MentionService mentionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new MentionController(mentionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Mention list API passes workspace id and user id to service")
    void getMyMentions() throws Exception {
        MentionResponse response = response(false);
        when(mentionService.getMyMentions(10L, USER_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/mentions", 10L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(300L))
                .andExpect(jsonPath("$.data[0].threadId").value(100L))
                .andExpect(jsonPath("$.data[0].mentionedByName").value("Sender"))
                .andExpect(jsonPath("$.data[0].read").value(false));

        verify(mentionService).getMyMentions(10L, USER_ID);
    }

    @Test
    @DisplayName("Mention read API passes mention id and user id to service")
    void markMentionAsRead() throws Exception {
        MentionResponse response = response(true);
        when(mentionService.markMentionAsRead(300L, USER_ID)).thenReturn(response);

        mockMvc.perform(patch("/api/mentions/{mentionId}/read", 300L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(300L))
                .andExpect(jsonPath("$.data.read").value(true));

        verify(mentionService).markMentionAsRead(300L, USER_ID);
    }

    @Test
    @DisplayName("Mention delete API passes mention id and user id to service")
    void deleteMention() throws Exception {
        mockMvc.perform(delete("/api/mentions/{mentionId}", 300L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(mentionService).deleteMention(300L, USER_ID);
    }

    private MentionResponse response(boolean read) {
        return new MentionResponse(
                300L,
                10L,
                1L,
                100L,
                null,
                21L,
                20L,
                "Sender",
                "hello @alice",
                read,
                LocalDateTime.of(2026, 6, 11, 12, 0)
        );
    }
}
