package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.channel.entity.Channel;
import com.team1.codedock.domain.chat.entity.Bookmark;
import com.team1.codedock.domain.chat.entity.Mention;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.Workspace;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatContentResponseDtoTest {

    @Test
    @DisplayName("채널 메시지 응답은 저장 토큰을 원문 이모지로 복원하고 첨부 null은 빈 목록으로 반환한다")
    void channelMessageResponseDecodesEmojiTokenContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        Thread message = message(
                100L,
                channel,
                sender,
                ChatContentEmojiCodec.encode("배포 완료 👍🔥"),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        );

        ChannelMessageResponse response = ChannelMessageResponse.from(message, null);

        assertThat(message.getContent()).isEqualTo("배포 완료 [[emoji:like]][[emoji:fire]]");
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.channelId()).isEqualTo(10L);
        assertThat(response.senderMemberId()).isEqualTo(20L);
        assertThat(response.senderName()).isEqualTo("보낸사람");
        assertThat(response.content()).isEqualTo("배포 완료 👍🔥");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 0));
        assertThat(response.attachments()).isEmpty();
    }

    @Test
    @DisplayName("채널 메시지 응답은 첨부 목록을 유지하면서 본문 이모지만 복원한다")
    void channelMessageResponseKeepsAttachmentsWhileDecodingContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        Thread message = message(
                100L,
                channel,
                sender,
                ChatContentEmojiCodec.encode("첨부 확인 📝"),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        );
        ThreadAttachmentResponse attachment = new ThreadAttachmentResponse(
                1L,
                "image",
                null,
                "https://example.com/image.png",
                "image.png",
                null,
                null,
                null,
                "image/png",
                100L,
                LocalDateTime.of(2026, 6, 18, 10, 1)
        );

        ChannelMessageResponse response = ChannelMessageResponse.from(message, List.of(attachment));

        assertThat(response.content()).isEqualTo("첨부 확인 📝");
        assertThat(response.attachments()).containsExactly(attachment);
    }

    @Test
    @DisplayName("답글 응답은 저장 토큰을 원문 이모지로 복원한다")
    void threadReplyResponseDecodesEmojiTokenContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        Thread message = message(100L, channel, sender, "parent", LocalDateTime.of(2026, 6, 18, 10, 0));
        ThreadReply reply = reply(
                200L,
                message,
                sender,
                ChatContentEmojiCodec.encode("수정했습니다 🔧✅"),
                LocalDateTime.of(2026, 6, 18, 10, 2)
        );

        ThreadReplyResponse response = ThreadReplyResponse.from(reply);

        assertThat(reply.getContent()).isEqualTo("수정했습니다 [[emoji:fix]][[emoji:check]]");
        assertThat(response.id()).isEqualTo(200L);
        assertThat(response.threadId()).isEqualTo(100L);
        assertThat(response.senderMemberId()).isEqualTo(20L);
        assertThat(response.senderName()).isEqualTo("보낸사람");
        assertThat(response.content()).isEqualTo("수정했습니다 🔧✅");
        assertThat(response.createdAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 2));
    }

    @Test
    @DisplayName("멘션 응답은 채널 메시지 본문 저장 토큰을 원문 이모지로 복원한다")
    void mentionResponseDecodesThreadEmojiTokenContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        WorkspaceMember mentioned = member(21L, workspace, user("alice@test.com", "앨리스"));
        Thread message = message(
                100L,
                channel,
                sender,
                ChatContentEmojiCodec.encode("@alice 확인 부탁해요 👀"),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        );
        Mention mention = Mention.createForThread(workspace, message, mentioned, sender);
        ReflectionTestUtils.setField(mention, "id", 300L);
        ReflectionTestUtils.setField(mention, "createdAt", LocalDateTime.of(2026, 6, 18, 10, 3));

        MentionResponse response = MentionResponse.from(mention);

        assertThat(response.id()).isEqualTo(300L);
        assertThat(response.workspaceId()).isEqualTo(1L);
        assertThat(response.channelId()).isEqualTo(10L);
        assertThat(response.threadId()).isEqualTo(100L);
        assertThat(response.threadReplyId()).isNull();
        assertThat(response.mentionedMemberId()).isEqualTo(21L);
        assertThat(response.mentionedByMemberId()).isEqualTo(20L);
        assertThat(response.mentionedByName()).isEqualTo("보낸사람");
        assertThat(response.content()).isEqualTo("@alice 확인 부탁해요 👀");
        assertThat(response.read()).isFalse();
    }

    @Test
    @DisplayName("멘션 응답은 답글 본문 저장 토큰을 원문 이모지로 복원한다")
    void mentionResponseDecodesReplyEmojiTokenContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        WorkspaceMember mentioned = member(21L, workspace, user("alice@test.com", "앨리스"));
        Thread message = message(100L, channel, sender, "parent", LocalDateTime.of(2026, 6, 18, 10, 0));
        ThreadReply reply = reply(
                200L,
                message,
                sender,
                ChatContentEmojiCodec.encode("@alice 좋아요 👍"),
                LocalDateTime.of(2026, 6, 18, 10, 2)
        );
        Mention mention = Mention.createForThreadReply(workspace, reply, mentioned, sender);
        ReflectionTestUtils.setField(mention, "id", 301L);

        MentionResponse response = MentionResponse.from(mention);

        assertThat(response.threadId()).isEqualTo(100L);
        assertThat(response.threadReplyId()).isEqualTo(200L);
        assertThat(response.content()).isEqualTo("@alice 좋아요 👍");
    }

    @Test
    @DisplayName("북마크 응답은 저장된 메시지 본문 토큰을 원문 이모지로 복원한다")
    void bookmarkResponseDecodesMessageEmojiTokenContent() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        WorkspaceMember sender = member(20L, workspace, user("sender@test.com", "보낸사람"));
        Thread message = message(
                100L,
                channel,
                sender,
                ChatContentEmojiCodec.encode("즐겨찾기 ⭐"),
                LocalDateTime.of(2026, 6, 18, 10, 0)
        );
        Bookmark bookmark = Bookmark.create(sender, message);
        ReflectionTestUtils.setField(bookmark, "id", 400L);
        ReflectionTestUtils.setField(bookmark, "createdAt", LocalDateTime.of(2026, 6, 18, 10, 4));

        BookmarkResponse response = BookmarkResponse.from(bookmark);

        assertThat(response.bookmarkId()).isEqualTo(400L);
        assertThat(response.channelId()).isEqualTo(10L);
        assertThat(response.messageId()).isEqualTo(100L);
        assertThat(response.senderMemberId()).isEqualTo(20L);
        assertThat(response.senderName()).isEqualTo("보낸사람");
        assertThat(response.content()).isEqualTo("즐겨찾기 ⭐");
        assertThat(response.messageCreatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 0));
        assertThat(response.bookmarkedAt()).isEqualTo(LocalDateTime.of(2026, 6, 18, 10, 4));
    }

    @Test
    @DisplayName("발신자 이름은 displayName, nickname, username 순서로 결정한다")
    void responseSenderNameFallsBackToNicknameAndUsername() {
        Workspace workspace = workspace(1L);
        Channel channel = channel(10L, workspace);
        User nicknameUser = user("nick@test.com", " ");
        ReflectionTestUtils.setField(nicknameUser, "nickname", "닉네임");
        WorkspaceMember nicknameMember = member(20L, workspace, nicknameUser);
        Thread nicknameMessage = message(100L, channel, nicknameMember, "hello", LocalDateTime.now());

        User usernameUser = user("user@test.com", "");
        ReflectionTestUtils.setField(usernameUser, "nickname", "");
        WorkspaceMember usernameMember = member(21L, workspace, usernameUser);
        Thread usernameMessage = message(101L, channel, usernameMember, "hello", LocalDateTime.now());

        assertThat(ChannelMessageResponse.from(nicknameMessage).senderName()).isEqualTo("닉네임");
        assertThat(ChannelMessageResponse.from(usernameMessage).senderName()).isEqualTo("user@test.com");
    }

    private static Thread message(
            Long id,
            Channel channel,
            WorkspaceMember sender,
            String content,
            LocalDateTime createdAt
    ) {
        Thread message = Thread.createChannelMessage(channel, sender, content);
        ReflectionTestUtils.setField(message, "id", id);
        ReflectionTestUtils.setField(message, "createdAt", createdAt);
        return message;
    }

    private static ThreadReply reply(
            Long id,
            Thread thread,
            WorkspaceMember sender,
            String content,
            LocalDateTime createdAt
    ) {
        ThreadReply reply = ThreadReply.create(thread, sender, content);
        ReflectionTestUtils.setField(reply, "id", id);
        ReflectionTestUtils.setField(reply, "createdAt", createdAt);
        return reply;
    }

    private static Channel channel(Long id, Workspace workspace) {
        Channel channel = Channel.createCustom(workspace, "team-chat", null);
        ReflectionTestUtils.setField(channel, "id", id);
        return channel;
    }

    private static Workspace workspace(Long id) {
        User owner = user("owner@test.com", "오너");
        Workspace workspace = Workspace.create(owner, "team", "team", null);
        ReflectionTestUtils.setField(workspace, "id", id);
        return workspace;
    }

    private static WorkspaceMember member(Long id, Workspace workspace, User user) {
        WorkspaceMember member = WorkspaceMember.create(workspace, user, "editor");
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static User user(String email, String displayName) {
        return User.create(email, "encoded-password", displayName);
    }
}
