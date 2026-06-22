package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

import java.time.LocalDateTime;

public record ThreadReplyResponse(
        Long id,
        Long threadId,
        Long senderMemberId,
        String senderName,
        String senderAvatarUrl,
        String content,
        LocalDateTime createdAt,
        boolean isDeleted
) {

    public ThreadReplyResponse(
            Long id,
            Long threadId,
            Long senderMemberId,
            String senderName,
            String content,
            LocalDateTime createdAt
    ) {
        this(id, threadId, senderMemberId, senderName, null, content, createdAt, false);
    }

    public static ThreadReplyResponse from(ThreadReply reply) {
        WorkspaceMember sender = reply.getWorkspaceMember();
        User user = sender.getUser();

        return new ThreadReplyResponse(
                reply.getId(),
                reply.getThread().getId(),
                sender.getId(),
                resolveSenderName(user),
                user.getAvatarUrl(),
                ChatContentEmojiCodec.decode(reply.getContent()),
                reply.getCreatedAt(),
                reply.isDeleted()
        );
    }

    private static String resolveSenderName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }
}
