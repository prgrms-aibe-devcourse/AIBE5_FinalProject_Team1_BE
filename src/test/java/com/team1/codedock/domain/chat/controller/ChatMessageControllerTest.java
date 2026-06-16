package com.team1.codedock.domain.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChannelMessageRestCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageUpdateRequest;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentListRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentRequest;
import com.team1.codedock.domain.chat.dto.ThreadAttachmentResponse;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.domain.chat.service.ThreadAttachmentService;
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
import org.springframework.http.MediaType;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatMessageControllerTest {

    private static final Long USER_ID = 10L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ThreadAttachmentService threadAttachmentService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        lenient().when(userDetails.getUserId()).thenReturn(USER_ID);
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_USER"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, authorities));

        mockMvc = MockMvcBuilders.standaloneSetup(new ChatMessageController(
                        chatMessageService,
                        threadAttachmentService,
                        messagingTemplate
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new CharacterEncodingFilter("UTF-8", true))
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Channel message list API passes user id and paging params to service")
    void getChannelMessages() throws Exception {
        Long channelId = 1L;
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

        when(chatMessageService.getChannelMessages(channelId, USER_ID, cursor, limit)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                        .header("X-User-Id", "999")
                        .param("cursor", String.valueOf(cursor))
                        .param("limit", String.valueOf(limit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(101L))
                .andExpect(jsonPath("$.data[0].content").value("hello"));

        verify(chatMessageService).getChannelMessages(channelId, USER_ID, cursor, limit);
    }

    @Test
    @DisplayName("Channel message create API passes request body and user id to service")
    void createChannelMessage() throws Exception {
        Long channelId = 1L;
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                101L,
                channelId,
                20L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.createChannelMessage(eq(channelId), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(101L))
                .andExpect(jsonPath("$.data.content").value("hello"));

        verify(chatMessageService).createChannelMessage(channelId, USER_ID, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/events",
                ChatEventType.MESSAGE_CREATED,
                response
        );
    }

    @Test
    @DisplayName("Channel message create API ignores X-User-Id and uses authenticated user id")
    void createChannelMessageIgnoresXUserIdHeader() throws Exception {
        Long channelId = 1L;
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest("hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                101L,
                channelId,
                20L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.createChannelMessage(eq(channelId), eq(USER_ID), eq(request))).thenReturn(response);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(chatMessageService).createChannelMessage(channelId, USER_ID, request);
    }

    @Test
    @DisplayName("Message attachment create API passes request body and user id to service")
    void addMessageAttachments() throws Exception {
        Long channelId = 1L;
        Long messageId = 101L;
        ThreadAttachmentRequest attachmentRequest = new ThreadAttachmentRequest(
                "image",
                null,
                "https://example.com/image.png",
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L
        );
        ThreadAttachmentListRequest request = new ThreadAttachmentListRequest(List.of(attachmentRequest));
        ThreadAttachmentResponse response = new ThreadAttachmentResponse(
                1L,
                "image",
                null,
                "https://example.com/image.png",
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadAttachmentService.addAttachments(channelId, messageId, USER_ID, request.attachments()))
                .thenReturn(List.of(response));

        mockMvc.perform(post("/api/channels/{channelId}/messages/{messageId}/attachments", channelId, messageId)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1L))
                .andExpect(jsonPath("$.data[0].attachmentType").value("image"));

        verify(threadAttachmentService).addAttachments(channelId, messageId, USER_ID, request.attachments());
    }

    @Test
    @DisplayName("Message attachment create API accepts frontend type and size fields")
    void addMessageAttachmentsWithFrontendFields() throws Exception {
        Long channelId = 1L;
        Long messageId = 101L;

        mockMvc.perform(post("/api/channels/{channelId}/messages/{messageId}/attachments", channelId, messageId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "attachments": [
                                    {
                                      "type": "image",
                                      "url": "blob:http://localhost/image",
                                      "title": "image.png",
                                      "detail": "image/png",
                                      "meta": "100 B",
                                      "previewUrl": "blob:http://localhost/image",
                                      "mimeType": "image/png",
                                      "size": 100
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<List<ThreadAttachmentRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(threadAttachmentService).addAttachments(eq(channelId), eq(messageId), eq(USER_ID), captor.capture());
        assertThat(captor.getValue().get(0).attachmentType()).isEqualTo("image");
        assertThat(captor.getValue().get(0).fileSize()).isEqualTo(100L);
    }

    @Test
    @DisplayName("Channel message create API rejects blank content")
    void createChannelMessageWithInvalidContent() throws Exception {
        ChannelMessageRestCreateRequest request = new ChannelMessageRestCreateRequest(" ");

        mockMvc.perform(post("/api/channels/{channelId}/messages", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Channel message create API rejects null attachment item")
    void createChannelMessageWithNullAttachmentItem() throws Exception {
        ChannelMessageRestCreateRequest request =
                new ChannelMessageRestCreateRequest("hello", java.util.Arrays.asList((ThreadAttachmentRequest) null));

        mockMvc.perform(post("/api/channels/{channelId}/messages", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Message attachment create API rejects null attachment item")
    void addMessageAttachmentsWithNullAttachmentItem() throws Exception {
        ThreadAttachmentListRequest request =
                new ThreadAttachmentListRequest(java.util.Arrays.asList((ThreadAttachmentRequest) null));

        mockMvc.perform(post("/api/channels/{channelId}/messages/{messageId}/attachments", 1L, 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Channel message update API broadcasts MESSAGE_UPDATED after service success")
    void updateChannelMessage() throws Exception {
        Long channelId = 1L;
        Long messageId = 101L;
        ChannelMessageUpdateRequest request = new ChannelMessageUpdateRequest("updated");
        ChannelMessageResponse response = new ChannelMessageResponse(
                messageId,
                channelId,
                20L,
                "tester",
                "updated",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.updateChannelMessage(eq(channelId), eq(messageId), eq(USER_ID), eq(request)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                        .header("X-User-Id", "999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(messageId))
                .andExpect(jsonPath("$.data.content").value("updated"));

        verify(chatMessageService).updateChannelMessage(channelId, messageId, USER_ID, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/events",
                ChatEventType.MESSAGE_UPDATED,
                response
        );
    }

    @Test
    @DisplayName("Channel message update API rejects blank content")
    void updateChannelMessageWithInvalidContent() throws Exception {
        ChannelMessageUpdateRequest request = new ChannelMessageUpdateRequest(" ");

        mockMvc.perform(patch("/api/channels/{channelId}/messages/{messageId}", 1L, 101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    @DisplayName("Channel message delete API broadcasts MESSAGE_DELETED after service success")
    void deleteChannelMessage() throws Exception {
        Long channelId = 1L;
        Long messageId = 101L;
        ChannelMessageResponse response = new ChannelMessageResponse(
                messageId,
                channelId,
                20L,
                "tester",
                Thread.DELETED_MESSAGE_CONTENT,
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(chatMessageService.deleteChannelMessage(channelId, messageId, USER_ID)).thenReturn(response);

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                        .header("X-User-Id", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(messageId))
                .andExpect(jsonPath("$.data.content").value(Thread.DELETED_MESSAGE_CONTENT));

        verify(chatMessageService).deleteChannelMessage(channelId, messageId, USER_ID);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/events",
                ChatEventType.MESSAGE_DELETED,
                response
        );
    }

    private void assertBroadcastEvent(
            String destination,
            ChatEventType expectedType,
            ChannelMessageResponse expectedPayload
    ) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);

        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(expectedType);
        assertThat(event.payload()).isEqualTo(expectedPayload);
    }
}
