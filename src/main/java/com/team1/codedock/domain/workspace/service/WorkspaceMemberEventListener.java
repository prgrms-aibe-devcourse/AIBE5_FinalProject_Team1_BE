package com.team1.codedock.domain.workspace.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class WorkspaceMemberEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendAfterCommit(WorkspaceMemberEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/workspaces/" + event.workspaceId() + "/members",
                event.payload()
        );
    }
}