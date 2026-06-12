package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatNotificationService chatNotificationService;

    @Test
    @DisplayName("Sends personal notification with NOTIFICATION_CREATED envelope")
    void sendNotification() {
        String userDestinationKey = "tester@example.com";
        ChatNotificationResponse notification = notification();

        chatNotificationService.sendNotification(userDestinationKey, notification);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq(userDestinationKey),
                eq("/queue/notifications"),
                payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue()).isInstanceOf(ChatEventResponse.class);
        ChatEventResponse<?> event = (ChatEventResponse<?>) payloadCaptor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.NOTIFICATION_CREATED);
        assertThat(event.payload()).isEqualTo(notification);
    }

    @Test
    @DisplayName("Rejects personal notification without user destination key")
    void sendNotificationWithoutUserDestinationKey() {
        ChatNotificationResponse notification = notification();

        assertThatThrownBy(() -> chatNotificationService.sendNotification(" ", notification))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("Rejects personal notification without payload")
    void sendNotificationWithoutPayload() {
        assertThatThrownBy(() -> chatNotificationService.sendNotification("tester@example.com", null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);

        verify(messagingTemplate, never()).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private static ChatNotificationResponse notification() {
        return new ChatNotificationResponse(
                1L,
                2L,
                3L,
                null,
                4L,
                "새 멘션이 도착했습니다.",
                LocalDateTime.of(2026, 6, 12, 11, 30)
        );
    }
}
