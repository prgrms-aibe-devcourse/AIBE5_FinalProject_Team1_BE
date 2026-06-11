package com.team1.codedock.domain.channel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.service.ChannelCommandService;
import com.team1.codedock.domain.channel.service.ChannelQueryService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChannelQueryService channelQueryService;

    @Mock
    private ChannelCommandService channelCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelController(channelQueryService, channelCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @Test
    @DisplayName("Channel list API returns workspace channels")
    void getChannels() throws Exception {
        ChannelListResponse response = new ChannelListResponse(
                1L,
                10L,
                null,
                "general",
                "general",
                false,
                "General channel",
                "hello",
                LocalDateTime.of(2026, 6, 11, 10, 0),
                3L,
                2L
        );
        when(channelQueryService.getChannels(10L, 100L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/channels", 10L)
                        .header("X-User-Id", 100L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("general"))
                .andExpect(jsonPath("$.data[0].lastMessage").value("hello"))
                .andExpect(jsonPath("$.data[0].messageCount").value(3L))
                .andExpect(jsonPath("$.data[0].unreadCount").value(2L));
    }

    @Test
    @DisplayName("Channel create API passes request to service")
    void createChannel() throws Exception {
        ChannelCreateRequest request = new ChannelCreateRequest("team-chat", "Team chat");
        ChannelListResponse response = new ChannelListResponse(
                2L,
                10L,
                null,
                "team-chat",
                "custom",
                true,
                "Team chat",
                null,
                null,
                0L,
                0L
        );
        when(channelCommandService.createChannel(eq(10L), eq(100L), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/channels", 10L)
                        .header("X-User-Id", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.name").value("team-chat"));

        verify(channelCommandService).createChannel(10L, 100L, request);
    }

    @Test
    @DisplayName("Channel create API rejects blank name")
    void createChannelWithBlankName() throws Exception {
        ChannelCreateRequest request = new ChannelCreateRequest(" ", "Team chat");

        mockMvc.perform(post("/api/workspaces/{workspaceId}/channels", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Channel update API passes request to service")
    void updateChannel() throws Exception {
        ChannelUpdateRequest request = new ChannelUpdateRequest("renamed", "Updated");
        ChannelListResponse response = new ChannelListResponse(
                2L,
                10L,
                null,
                "renamed",
                "custom",
                true,
                "Updated",
                null,
                null,
                0L,
                0L
        );
        when(channelCommandService.updateChannel(eq(10L), eq(2L), eq(100L), eq(request))).thenReturn(response);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/channels/{channelId}", 10L, 2L)
                        .header("X-User-Id", 100L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.name").value("renamed"));

        verify(channelCommandService).updateChannel(10L, 2L, 100L, request);
    }

    @Test
    @DisplayName("Channel delete API passes path variables to service")
    void deleteChannel() throws Exception {
        mockMvc.perform(delete("/api/workspaces/{workspaceId}/channels/{channelId}", 10L, 2L)
                        .header("X-User-Id", 100L))
                .andExpect(status().isNoContent());

        verify(channelCommandService).deleteChannel(10L, 2L, 100L);
    }
}
