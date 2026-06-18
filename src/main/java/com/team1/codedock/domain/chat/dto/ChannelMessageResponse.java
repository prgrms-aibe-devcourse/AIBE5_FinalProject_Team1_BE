package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.chat.util.ChatContentEmojiCodec;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

import java.time.LocalDateTime;
import java.util.List;

public record ChannelMessageResponse(
        Long id,
        Long channelId,
        Long senderMemberId,
        String senderName,
        String content,
        LocalDateTime createdAt,
        List<ThreadAttachmentResponse> attachments
) {

    public ChannelMessageResponse(
            Long id,
            Long channelId,
            Long senderMemberId,
            String senderName,
            String content,
            LocalDateTime createdAt
    ) {
        this(id, channelId, senderMemberId, senderName, content, createdAt, List.of());
    }

    public static ChannelMessageResponse from(Thread thread) {
        return from(thread, List.of());
    }

    public static ChannelMessageResponse from(Thread thread, List<ThreadAttachmentResponse> attachments) {
        WorkspaceMember sender = thread.getCreatedBy();
        User user = sender.getUser();

        return new ChannelMessageResponse(
                thread.getId(),
                thread.getChannel().getId(),
                sender.getId(),
                resolveSenderName(user),
                ChatContentEmojiCodec.decode(thread.getContent()),
                thread.getCreatedAt(),
                attachments == null ? List.of() : attachments
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
