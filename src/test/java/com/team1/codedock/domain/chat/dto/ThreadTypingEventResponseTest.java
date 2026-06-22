package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class ThreadTypingEventResponseTest {

    @Test
    @DisplayName("스레드 typing 응답은 displayName을 senderName으로 우선 사용한다")
    void ofUsesDisplayNameFirst() {
        WorkspaceMember sender = sender("display-name", "nickname", "tester@example.com");

        ThreadTypingEventResponse response = ThreadTypingEventResponse.of(
                100L,
                sender,
                new TypingEventRequest(true)
        );

        assertThat(response.threadId()).isEqualTo(100L);
        assertThat(response.workspaceMemberId()).isEqualTo(10L);
        assertThat(response.senderName()).isEqualTo("display-name");
        assertThat(response.typing()).isTrue();
    }

    @Test
    @DisplayName("스레드 typing 응답은 displayName이 비어 있으면 nickname을 사용한다")
    void ofUsesNicknameWhenDisplayNameIsBlank() {
        WorkspaceMember sender = sender(" ", "nickname", "tester@example.com");

        ThreadTypingEventResponse response = ThreadTypingEventResponse.of(
                100L,
                sender,
                new TypingEventRequest(true)
        );

        assertThat(response.senderName()).isEqualTo("nickname");
    }

    @Test
    @DisplayName("스레드 typing 응답은 displayName과 nickname이 비어 있으면 username을 사용한다")
    void ofUsesUsernameWhenDisplayNameAndNicknameAreBlank() {
        WorkspaceMember sender = sender(null, " ", "tester@example.com");

        ThreadTypingEventResponse response = ThreadTypingEventResponse.of(
                100L,
                sender,
                new TypingEventRequest(false)
        );

        assertThat(response.senderName()).isEqualTo("tester@example.com");
        assertThat(response.typing()).isFalse();
    }

    private WorkspaceMember sender(String displayName, String nickname, String email) {
        User user = User.create(email, "password", displayName);
        user.updateProfile(displayName, nickname, null, null, null);
        WorkspaceMember sender = WorkspaceMember.create(null, user, "editor");
        ReflectionTestUtils.setField(sender, "id", 10L);
        return sender;
    }
}
