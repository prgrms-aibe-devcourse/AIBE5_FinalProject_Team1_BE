package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.service.ChannelReadStatusService;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelReadStatusControllerTest {

    @Mock
    private ChannelReadStatusService channelReadStatusService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelReadStatusController(channelReadStatusService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("Channel read API passes channel id and X-User-Id to service")
    void markChannelAsRead() throws Exception {
        ChannelReadStatusResponse response = new ChannelReadStatusResponse(
                1L,
                20L,
                100L,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );

        when(channelReadStatusService.markChannelAsRead(1L, 30L)).thenReturn(response);

        mockMvc.perform(put("/api/channels/{channelId}/read", 1L)
                        .header("X-User-Id", 30L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channelId").value(1L))
                .andExpect(jsonPath("$.data.workspaceMemberId").value(20L))
                .andExpect(jsonPath("$.data.lastReadThreadId").value(100L));

        verify(channelReadStatusService).markChannelAsRead(1L, 30L);
    }
}
