package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ThreadReplyCreateRequest;
import com.team1.codedock.domain.chat.dto.ThreadReplyResponse;
import com.team1.codedock.domain.chat.dto.ThreadReplyWebSocketCreateRequest;
import com.team1.codedock.domain.chat.dto.TypingEventRequest;
import com.team1.codedock.domain.chat.dto.TypingEventResponse;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import com.team1.codedock.domain.chat.service.ThreadReplyService;
import com.team1.codedock.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ThreadReplyService threadReplyService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    @Test
    @DisplayName("Channel message WebSocket send broadcasts MESSAGE_CREATED event")
    void createChannelMessage() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest("hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                100L,
                channelId,
                10L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 8, 10, 0)
        );

        when(chatMessageService.createChannelMessage(channelId, userId, request)).thenReturn(response);

        chatWebSocketController.createChannelMessage(channelId, principal, request);

        verify(chatMessageService).createChannelMessage(channelId, userId, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/events",
                ChatEventType.MESSAGE_CREATED,
                response
        );
    }

    @Test
    @DisplayName("Thread reply WebSocket send broadcasts THREAD_REPLY_CREATED event")
    void createThreadReply() {
        Long threadId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        ThreadReplyWebSocketCreateRequest request = new ThreadReplyWebSocketCreateRequest("reply");
        ThreadReplyCreateRequest serviceRequest = new ThreadReplyCreateRequest("reply");
        ThreadReplyResponse response = new ThreadReplyResponse(
                100L,
                threadId,
                20L,
                "tester",
                "reply",
                LocalDateTime.of(2026, 6, 9, 10, 0)
        );

        when(threadReplyService.createReply(eq(threadId), eq(userId), eq(serviceRequest))).thenReturn(response);

        chatWebSocketController.createThreadReply(threadId, principal, request);

        verify(threadReplyService).createReply(threadId, userId, serviceRequest);
        assertBroadcastEvent(
                "/topic/threads/" + threadId + "/events",
                ChatEventType.THREAD_REPLY_CREATED,
                response
        );
    }

    @Test
    @DisplayName("Typing WebSocket send broadcasts TYPING event")
    void sendTypingEvent() {
        Long channelId = 1L;
        Long userId = 10L;
        Principal principal = principal(userId);
        TypingEventRequest request = new TypingEventRequest("tester", true);
        TypingEventResponse response = new TypingEventResponse(channelId, 10L, "tester", true);

        when(chatMessageService.createTypingEventResponse(channelId, userId, request)).thenReturn(response);

        chatWebSocketController.sendTypingEvent(channelId, principal, request);

        verify(chatMessageService).createTypingEventResponse(channelId, userId, request);
        assertBroadcastEvent(
                "/topic/channels/" + channelId + "/typing",
                ChatEventType.TYPING,
                response
        );
    }

    private void assertBroadcastEvent(
            String destination,
            ChatEventType expectedType,
            Object expectedPayload
    ) {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(destination), payloadCaptor.capture());

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);

        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(expectedType);
        assertThat(event.payload()).isEqualTo(expectedPayload);
    }

    private static Principal principal(Long userId) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        when(userDetails.getUserId()).thenReturn(userId);
        return new UsernamePasswordAuthenticationToken(userDetails, null);
    }
}
