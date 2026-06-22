package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelReadStatusResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.service.ChannelReadStatusService;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelReadStatusControllerTest {

    private static final Long USER_ID = 30L;

    @Mock
    private ChannelReadStatusService channelReadStatusService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(USER_ID);
        when(userDetails.getUsername()).thenReturn("reader@test.com");
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelReadStatusController(channelReadStatusService, messagingTemplate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Channel read API passes channel id and user id to service")
    void markChannelAsRead() throws Exception {
        ChannelReadStatusResponse response = new ChannelReadStatusResponse(
                1L,
                20L,
                100L,
                LocalDateTime.of(2026, 6, 11, 10, 0)
        );

        when(channelReadStatusService.markChannelAsRead(1L, USER_ID)).thenReturn(response);

        mockMvc.perform(put("/api/channels/{channelId}/read", 1L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channelId").value(1L))
                .andExpect(jsonPath("$.data.workspaceMemberId").value(20L))
                .andExpect(jsonPath("$.data.lastReadThreadId").value(100L));

        verify(channelReadStatusService).markChannelAsRead(1L, USER_ID);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("reader@test.com"),
                eq("/queue/notifications"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.CHANNEL_READ_STATUS_UPDATED);
        assertThat(event.payload()).isEqualTo(response);
    }
}
