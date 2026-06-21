package com.team1.codedock.domain.workspace.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class InviteNotificationEventListenerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private InviteNotificationEventListener listener;

    @Test
    @DisplayName("커밋 후 초대 알림 이벤트를 개인 워크스페이스 큐로 전송한다")
    void sendAfterCommit() {
        Map<String, Object> payload = Map.of(
                "type", "INVITE_EVENT",
                "action", "RECEIVED",
                "workspaceId", 10L
        );
        InviteNotificationEvent event = new InviteNotificationEvent("invitee@example.com", payload);

        listener.sendAfterCommit(event);

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(
                eq("invitee@example.com"),
                eq("/queue/workspace"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).isSameAs(payload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("개인 destination key가 null이면 초대 알림을 전송하지 않는다")
    void sendAfterCommitWithNullUserDestinationKey() {
        InviteNotificationEvent event = new InviteNotificationEvent(null, Map.of("type", "INVITE_EVENT"));

        listener.sendAfterCommit(event);

        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                anyString(),
                any()
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("개인 destination key가 blank이면 초대 알림을 전송하지 않는다")
    void sendAfterCommitWithBlankUserDestinationKey() {
        InviteNotificationEvent event = new InviteNotificationEvent(" ", Map.of("type", "INVITE_EVENT"));

        listener.sendAfterCommit(event);

        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                anyString(),
                any()
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("개인 destination key가 빈 문자열이면 초대 알림을 전송하지 않는다")
    void sendAfterCommitWithEmptyUserDestinationKey() {
        InviteNotificationEvent event = new InviteNotificationEvent("", Map.of("type", "INVITE_EVENT"));

        listener.sendAfterCommit(event);

        verify(messagingTemplate, never()).convertAndSendToUser(
                anyString(),
                anyString(),
                any()
        );
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("초대 알림 이벤트는 트랜잭션 커밋 이후에만 전송한다")
    void sendAfterCommitUsesAfterCommitTransactionalEventListener() throws NoSuchMethodException {
        Method method = InviteNotificationEventListener.class.getMethod("sendAfterCommit", InviteNotificationEvent.class);
        TransactionalEventListener annotation = method.getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }
}
