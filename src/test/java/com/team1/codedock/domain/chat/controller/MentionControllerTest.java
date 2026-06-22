package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.MentionResponse;
import com.team1.codedock.domain.chat.service.MentionService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        lenient().when(userDetails.getUsername()).thenReturn("alice@test.com");
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new MentionController(mentionService, messagingTemplate))
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

        mockMvc.perform(get("/api/workspaces/{workspaceId}/mentions", 10L))
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

        mockMvc.perform(patch("/api/mentions/{mentionId}/read", 300L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(300L))
                .andExpect(jsonPath("$.data.read").value(true));

        verify(mentionService).markMentionAsRead(300L, USER_ID);
    }

    @Test
    @DisplayName("Mention read API returns forbidden response when service rejects ownership")
    void markMentionAsReadForbidden() throws Exception {
        when(mentionService.markMentionAsRead(300L, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(patch("/api/mentions/{mentionId}/read", 300L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"));

        verify(mentionService).markMentionAsRead(300L, USER_ID);
    }

    @Test
    @DisplayName("Mention read API returns not found response when mention does not exist")
    void markMentionAsReadNotFound() throws Exception {
        when(mentionService.markMentionAsRead(300L, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(patch("/api/mentions/{mentionId}/read", 300L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"));

        verify(mentionService).markMentionAsRead(300L, USER_ID);
    }

    @Test
    @DisplayName("Mention delete API passes mention id and user id to service")
    void deleteMention() throws Exception {
        MentionResponse response = response(false);
        when(mentionService.deleteMention(300L, USER_ID)).thenReturn(response);

        mockMvc.perform(delete("/api/mentions/{mentionId}", 300L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(300L));

        verify(mentionService).deleteMention(300L, USER_ID);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("alice@test.com"),
                eq("/queue/notifications"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.MENTION_DELETED);
        assertThat(event.payload()).isEqualTo(response);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Mention delete API returns forbidden response when service rejects ownership")
    void deleteMentionForbidden() throws Exception {
        when(mentionService.deleteMention(300L, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        mockMvc.perform(delete("/api/mentions/{mentionId}", 300L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C003"));

        verify(mentionService).deleteMention(300L, USER_ID);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Mention delete API returns not found response when mention does not exist")
    void deleteMentionNotFound() throws Exception {
        when(mentionService.deleteMention(300L, USER_ID))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        mockMvc.perform(delete("/api/mentions/{mentionId}", 300L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"));

        verify(mentionService).deleteMention(300L, USER_ID);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("인증 사용자가 없으면 멘션 삭제 서비스와 WebSocket 전송을 수행하지 않는다")
    void deleteMentionWithoutAuthentication() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(delete("/api/mentions/{mentionId}", 300L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C002"));

        verifyNoInteractions(mentionService, messagingTemplate);
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
