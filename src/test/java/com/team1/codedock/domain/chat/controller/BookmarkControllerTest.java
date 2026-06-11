package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.BookmarkResponse;
import com.team1.codedock.domain.chat.dto.BookmarkToggleResponse;
import com.team1.codedock.domain.chat.service.BookmarkService;
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
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookmarkControllerTest {

    @Mock
    private BookmarkService bookmarkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookmarkController(bookmarkService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("Message bookmark toggle API passes path variables and X-User-Id to service")
    void toggleMessageBookmark() throws Exception {
        BookmarkToggleResponse response = new BookmarkToggleResponse(1L, 100L, 20L, true);

        when(bookmarkService.toggleMessageBookmark(1L, 100L, 30L)).thenReturn(response);

        mockMvc.perform(post("/api/channels/{channelId}/messages/{messageId}/bookmark", 1L, 100L)
                        .header("X-User-Id", 30L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.channelId").value(1L))
                .andExpect(jsonPath("$.data.messageId").value(100L))
                .andExpect(jsonPath("$.data.workspaceMemberId").value(20L))
                .andExpect(jsonPath("$.data.bookmarked").value(true));

        verify(bookmarkService).toggleMessageBookmark(1L, 100L, 30L);
    }

    @Test
    @DisplayName("Bookmark list API passes workspace id and X-User-Id to service")
    void getMyBookmarks() throws Exception {
        BookmarkResponse response = new BookmarkResponse(
                200L,
                1L,
                100L,
                20L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 11, 10, 0),
                LocalDateTime.of(2026, 6, 11, 11, 0)
        );

        when(bookmarkService.getMyBookmarks(10L, 30L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/bookmarks", 10L)
                        .header("X-User-Id", 30L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].bookmarkId").value(200L))
                .andExpect(jsonPath("$.data[0].messageId").value(100L))
                .andExpect(jsonPath("$.data[0].content").value("hello"));

        verify(bookmarkService).getMyBookmarks(10L, 30L);
    }
}
