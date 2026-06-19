package com.team1.codedock.domain.workspace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class InviteNotificationEventListener {

    private static final String PERSONAL_WORKSPACE_DESTINATION = "/queue/workspace";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(InviteNotificationEvent event) {
        if (event.userDestinationKey() == null || event.userDestinationKey().isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(
                event.userDestinationKey(),
                PERSONAL_WORKSPACE_DESTINATION,
                event.payload()
        );
    }
}