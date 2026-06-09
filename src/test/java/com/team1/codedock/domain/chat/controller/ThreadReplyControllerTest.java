package com.team1.codedock.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
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
class ThreadReplyControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ThreadReplyService threadReplyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThreadReplyController(threadReplyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("스레드 답글 목록 조회 API가 X-User-Id를 서비스로 전달한다")
    void getReplies() throws Exception {
        Long threadId = 1L;
        Long userId = 10L;
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.getReplies(threadId, userId)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/threads/{threadId}/replies", threadId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(100L))
                .andExpect(jsonPath("$.data[0].threadId").value(threadId))
                .andExpect(jsonPath("$.data[0].senderMemberId").value(20L))
                .andExpect(jsonPath("$.data[0].senderName").value("tester"))
                .andExpect(jsonPath("$.data[0].content").value("reply"));

        verify(threadReplyService).getReplies(threadId, userId);
    }

    @Test
    @DisplayName("스레드 답글 저장 API가 요청 본문과 X-User-Id를 서비스로 전달한다")
    void createReply() throws Exception {
        Long threadId = 1L;
        Long userId = 10L;
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("reply");
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.createReply(eq(threadId), eq(userId), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/threads/{threadId}/replies", threadId)
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100L))
                .andExpect(jsonPath("$.data.content").value("reply"));

        verify(threadReplyService).createReply(threadId, userId, request);
    }

    @Test
    @DisplayName("스레드 답글 저장 요청 내용이 비어 있으면 400 응답을 반환한다")
    void createReplyWithInvalidContent() throws Exception {
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest(" ");

        mockMvc.perform(post("/api/threads/{threadId}/replies", 1L)
                        .header("X-User-Id", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }
}
