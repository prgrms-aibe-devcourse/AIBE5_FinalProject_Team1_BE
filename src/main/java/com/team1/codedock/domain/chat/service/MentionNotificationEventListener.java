package com.team1.codedock.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MentionNotificationEventListener {

    private final ChatNotificationService chatNotificationService;

    // 멘션 저장 트랜잭션이 정상 커밋된 뒤에만 실제 WebSocket 알림 전송함
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(MentionNotificationEvent event) {
        chatNotificationService.sendNotification(event.userDestinationKey(), event.notification());
    }
}
