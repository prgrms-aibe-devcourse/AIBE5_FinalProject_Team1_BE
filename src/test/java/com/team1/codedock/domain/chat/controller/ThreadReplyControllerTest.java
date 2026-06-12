package com.team1.codedock.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyUpdateRequest;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
import com.team1.codedock.global.exception.GlobalExceptionHandler;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ThreadReplyControllerTest {

    private static final Long USER_ID = 10L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ThreadReplyService threadReplyService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new ThreadReplyController(threadReplyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Thread reply list API passes thread id and user id to service")
    void getReplies() throws Exception {
        Long threadId = 1L;
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.getReplies(threadId, USER_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/threads/{threadId}/replies", threadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(100L))
                .andExpect(jsonPath("$.data[0].threadId").value(threadId))
                .andExpect(jsonPath("$.data[0].senderMemberId").value(20L))
                .andExpect(jsonPath("$.data[0].senderName").value("tester"))
                .andExpect(jsonPath("$.data[0].content").value("reply"));

        verify(threadReplyService).getReplies(threadId, USER_ID);
    }

    @Test
    @DisplayName("Thread reply create API passes request body and user id to service")
    void createReply() throws Exception {
        Long threadId = 1L;
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest("reply");
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.createReply(eq(threadId), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/threads/{threadId}/replies", threadId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(100L))
                .andExpect(jsonPath("$.data.content").value("reply"));

        verify(threadReplyService).createReply(threadId, USER_ID, request);
    }

    @Test
    @DisplayName("Thread reply create API rejects blank content")
    void createReplyWithInvalidContent() throws Exception {
        ThreadReplyCreateRequest request = new ThreadReplyCreateRequest(" ");

        mockMvc.perform(post("/api/threads/{threadId}/replies", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Reply update API passes request body and user id to service")
    void updateReply() throws Exception {
        Long threadId = 1L;
        Long replyId = 100L;
        ThreadReplyUpdateRequest request = new ThreadReplyUpdateRequest("updated reply");
        ThreadReplyResponse response = new ThreadReplyResponse(
                replyId,
                threadId,
                20L,
                "tester",
                "updated reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.updateReply(eq(threadId), eq(replyId), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(patch("/api/threads/{threadId}/replies/{replyId}", threadId, replyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(replyId))
                .andExpect(jsonPath("$.data.threadId").value(threadId))
                .andExpect(jsonPath("$.data.content").value("updated reply"));

        verify(threadReplyService).updateReply(threadId, replyId, USER_ID, request);
    }

    @Test
    @DisplayName("Reply update API rejects blank content")
    void updateReplyWithInvalidContent() throws Exception {
        ThreadReplyUpdateRequest request = new ThreadReplyUpdateRequest(" ");

        mockMvc.perform(patch("/api/threads/{threadId}/replies/{replyId}", 1L, 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Reply delete API passes path variables and user id to service")
    void deleteReply() throws Exception {
        Long threadId = 1L;
        Long replyId = 100L;
        ThreadReplyResponse response = new ThreadReplyResponse(
                replyId,
                threadId,
                20L,
                "tester",
                ThreadReply.DELETED_REPLY_CONTENT,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.deleteReply(threadId, replyId, USER_ID)).thenReturn(response);

        mockMvc.perform(delete("/api/threads/{threadId}/replies/{replyId}", threadId, replyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(replyId))
                .andExpect(jsonPath("$.data.threadId").value(threadId))
                .andExpect(jsonPath("$.data.content").value(ThreadReply.DELETED_REPLY_CONTENT));

        verify(threadReplyService).deleteReply(threadId, replyId, USER_ID);
    }
}
