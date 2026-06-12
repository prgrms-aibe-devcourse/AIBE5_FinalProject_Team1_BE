package com.team1.codedock.domain.chat.service;

import com.team1.codedock.domain.chat.dto.ChatEventResponse;
import com.team1.codedock.domain.chat.dto.ChatEventType;
import com.team1.codedock.domain.chat.dto.ChatNotificationResponse;
import com.team1.codedock.global.exception.BusinessException;
import com.team1.codedock.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatNotificationService {

    private static final String PERSONAL_NOTIFICATION_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String userDestinationKey, ChatNotificationResponse notification) {
        validateUserDestinationKey(userDestinationKey);
        validateNotification(notification);

        // convertAndSendToUser는 클라이언트의 /user prefix를 제외한 목적지로 전송함
        messagingTemplate.convertAndSendToUser(
                userDestinationKey,
                PERSONAL_NOTIFICATION_DESTINATION,
                ChatEventResponse.of(ChatEventType.NOTIFICATION_CREATED, notification)
        );
    }

    private void validateUserDestinationKey(String userDestinationKey) {
        if (userDestinationKey == null || userDestinationKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알림 대상 사용자가 필요합니다.");
        }
    }

    private void validateNotification(ChatNotificationResponse notification) {
        if (notification == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "알림 내용이 필요합니다.");
        }
    }
}
