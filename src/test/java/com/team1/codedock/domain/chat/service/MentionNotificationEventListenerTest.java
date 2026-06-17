package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MentionNotificationEventListenerTest {

    @Mock
    private ChatNotificationService chatNotificationService;

    @InjectMocks
    private MentionNotificationEventListener listener;

    @Test
    @DisplayName("커밋 후 멘션 알림 이벤트를 개인 알림 서비스로 전송한다")
    void sendAfterCommit() {
        ChatNotificationResponse notification = new ChatNotificationResponse(
                1L,
                2L,
                3L,
                null,
                4L,
                "새 멘션이 도착했습니다.",
                LocalDateTime.of(2026, 6, 17, 11, 0)
        );
        MentionNotificationEvent event = new MentionNotificationEvent("tester@example.com", notification);

        listener.sendAfterCommit(event);

        verify(chatNotificationService).sendNotification("tester@example.com", notification);
    }
}
