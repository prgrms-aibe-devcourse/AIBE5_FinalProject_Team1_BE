package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.Mention;
import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.entity.ThreadReply;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

import java.time.LocalDateTime;

public record MentionResponse(
        Long id,
        Long workspaceId,
        Long channelId,
        Long threadId,
        Long threadReplyId,
        Long mentionedMemberId,
        Long mentionedByMemberId,
        String mentionedByName,
        String content,
        boolean read,
        LocalDateTime createdAt
) {
    public static MentionResponse from(Mention mention) {
        Thread thread = resolveThread(mention);
        ThreadReply reply = mention.getThreadReply();
        WorkspaceMember mentionedBy = mention.getMentionedByMember();

        return new MentionResponse(
                mention.getId(),
                mention.getWorkspace().getId(),
                thread.getChannel().getId(),
                thread.getId(),
                reply == null ? null : reply.getId(),
                mention.getMentionedMember().getId(),
                mentionedBy.getId(),
                resolveSenderName(mentionedBy.getUser()),
                reply == null ? thread.getContent() : reply.getContent(),
                mention.isRead(),
                mention.getCreatedAt()
        );
    }

    private static Thread resolveThread(Mention mention) {
        if (mention.getThread() != null) {
            return mention.getThread();
        }
        return mention.getThreadReply().getThread();
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
