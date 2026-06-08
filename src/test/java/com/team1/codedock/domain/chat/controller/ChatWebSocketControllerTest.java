package com.team1.codedock.domain.chat.controller;

import com.team1.codedock.domain.chat.dto.ChannelMessageCreateRequest;
import com.team1.codedock.domain.chat.dto.ChannelMessageResponse;
import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.service.ChatMessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    @Test
    @DisplayName("채널 메시지를 수신하면 메시지를 저장하고 MESSAGE_CREATED 이벤트를 브로드캐스트한다")
    void createChannelMessage() {
        Long channelId = 1L;
        ChannelMessageCreateRequest request = new ChannelMessageCreateRequest(10L, "hello");
        ChannelMessageResponse response = new ChannelMessageResponse(
                100L,
                channelId,
                10L,
                "tester",
                "hello",
                LocalDateTime.of(2026, 6, 8, 10, 0)
        );

        when(chatMessageService.createChannelMessage(channelId, request)).thenReturn(response);

        chatWebSocketController.createChannelMessage(channelId, request);

        verify(chatMessageService).createChannelMessage(channelId, request);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/topic/channels/" + channelId + "/events"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);

        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.MESSAGE_CREATED);
        assertThat(event.payload()).isEqualTo(response);
    }
}
