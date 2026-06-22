package com.team1.codedock.domain.channel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.channel.dto.ChannelCreateRequest;
import com.team1.codedock.domain.channel.dto.ChannelListResponse;
import com.team1.codedock.domain.channel.dto.ChannelOrderUpdateRequest;
import com.team1.codedock.domain.channel.dto.ChannelUpdateRequest;
import com.team1.codedock.domain.channel.service.ChannelCommandService;
import com.team1.codedock.domain.channel.service.ChannelQueryService;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChannelControllerTest {

    private static final Long USER_ID = 100L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChannelQueryService channelQueryService;

    @Mock
    private ChannelCommandService channelCommandService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new ChannelController(channelQueryService, channelCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
                0,
                "General channel",
                "hello",
                LocalDateTime.of(2026, 6, 11, 10, 0),
                3L,
                2L
        );
        when(channelQueryService.getChannels(10L, USER_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/workspaces/{workspaceId}/channels", 10L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].name").value("general"))
                .andExpect(jsonPath("$.data[0].lastMessage").value("hello"))
                .andExpect(jsonPath("$.data[0].messageCount").value(3L))
                .andExpect(jsonPath("$.data[0].unreadCount").value(2L));
    }

    @Test
    @DisplayName("Channel list API ignores X-User-Id and uses authenticated user id")
    void getChannelsIgnoresXUserIdHeader() throws Exception {
        when(channelQueryService.getChannels(10L, USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/workspaces/{workspaceId}/channels", 10L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(channelQueryService).getChannels(10L, USER_ID);
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
                1,
                "Team chat",
                null,
                null,
                0L,
                0L
        );
        when(channelCommandService.createChannel(eq(10L), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/workspaces/{workspaceId}/channels", 10L)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.name").value("team-chat"));

        verify(channelCommandService).createChannel(10L, USER_ID, request);
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
                1,
                "Updated",
                null,
                null,
                0L,
                0L
        );
        when(channelCommandService.updateChannel(eq(10L), eq(2L), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/channels/{channelId}", 10L, 2L)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(2L))
                .andExpect(jsonPath("$.data.name").value("renamed"));

        verify(channelCommandService).updateChannel(10L, 2L, USER_ID, request);
    }

    @Test
    @DisplayName("채널 순서 변경 API는 인증 사용자 기준으로 순서를 저장한다")
    void updateChannelOrder() throws Exception {
        ChannelOrderUpdateRequest request = new ChannelOrderUpdateRequest(List.of(3L, 1L, 2L));
        ChannelListResponse first = new ChannelListResponse(
                3L,
                10L,
                null,
                "docs",
                "custom",
                true,
                0,
                null,
                null,
                null,
                0L,
                0L
        );
        when(channelCommandService.updateChannelOrder(eq(10L), eq(USER_ID), eq(request))).thenReturn(List.of(first));

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/channels/order", 10L)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(3L))
                .andExpect(jsonPath("$.data[0].displayOrder").value(0));

        verify(channelCommandService).updateChannelOrder(10L, USER_ID, request);
    }

    @Test
    @DisplayName("채널 순서 변경 API는 빈 채널 목록이면 서비스 호출 전에 거부한다")
    void updateChannelOrderWithEmptyChannelIds() throws Exception {
        ChannelOrderUpdateRequest request = new ChannelOrderUpdateRequest(List.of());

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/channels/order", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(channelCommandService);
    }

    @Test
    @DisplayName("채널 순서 변경 API는 null 채널 id가 있으면 서비스 호출 전에 거부한다")
    void updateChannelOrderWithNullChannelId() throws Exception {
        String requestBody = "{\"channelIds\":[1,null,2]}";

        mockMvc.perform(patch("/api/workspaces/{workspaceId}/channels/order", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));

        verifyNoInteractions(channelCommandService);
    }

    @Test
    @DisplayName("Channel delete API passes path variables to service")
    void deleteChannel() throws Exception {
        mockMvc.perform(delete("/api/workspaces/{workspaceId}/channels/{channelId}", 10L, 2L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isNoContent());

        verify(channelCommandService).deleteChannel(10L, 2L, USER_ID);
    }

    @Test
    @DisplayName("Channel delete API supports v1 workspace path")
    void deleteChannelWithV1Path() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/channels/{channelId}", 10L, 2L)
                        .header("X-User-Id", "999"))
                .andExpect(status().isNoContent());

        verify(channelCommandService).deleteChannel(10L, 2L, USER_ID);
    }
}
