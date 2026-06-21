package com.team1.codedock.domain.workspace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class WorkspaceMemberEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WorkspaceMemberEventListener listener;

    @Test
    @DisplayName("커밋 후 워크스페이스 멤버 변경 이벤트를 members 토픽으로 전송한다")
    void sendAfterCommit() {
        Map<String, Object> payload = Map.of(
                "type", "MEMBER_EVENT",
                "action", "ROLE_CHANGED",
                "workspaceId", 10L
        );
        WorkspaceMemberEvent event = new WorkspaceMemberEvent(10L, payload);

        listener.sendAfterCommit(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(
                eq("/topic/workspaces/10/members"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
