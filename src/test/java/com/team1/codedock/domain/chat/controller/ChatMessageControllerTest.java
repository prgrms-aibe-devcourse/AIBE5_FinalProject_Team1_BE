package com.team1.codedock.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatMessageService chatMessageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatMessageController(chatMessageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("채널 메시지 목록 조회 API가 X-User-Id와 페이지 요청값을 서비스로 전달한다")
    void getChannelMessages() throws Exception {
        Long channelId = 1L;
        Long userId = 10L;
        Long cursor = 100L;
        int limit = 20;
        ChannelMessageResponse response = new ChannelMessageResponse(
                101L,
                channelId,
                20L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.getChannelMessages(channelId, userId, cursor, limit)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                        .header("X-User-Id", userId)
                        .param("cursor", String.valueOf(cursor))
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(101L))
                .andExpect(jsonPath("$.data[0].content").value("hello"));

        verify(chatMessageService).getChannelMessages(channelId, userId, cursor, limit);
    }

    @Test
    @DisplayName("채널 메시지 REST 저장 API가 요청 본문과 X-User-Id를 서비스로 전달한다")
    void createChannelMessage() throws Exception {
        Long channelId = 1L;
        Long userId = 10L;
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                101L,
                channelId,
                20L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.createChannelMessage(eq(channelId), eq(userId), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(101L))
                .andExpect(jsonPath("$.data.content").value("hello"));

        verify(chatMessageService).createChannelMessage(channelId, userId, request);
    }

    @Test
    @DisplayName("채널 메시지 REST 저장 요청 내용이 비어 있으면 400 응답을 반환한다")
    void createChannelMessageWithInvalidContent() throws Exception {
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest(" ");

        mockMvc.perform(post("/api/channels/{channelId}/messages", 1L)
                        .header("X-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
