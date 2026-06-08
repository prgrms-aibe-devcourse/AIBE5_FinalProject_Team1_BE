package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.chat.entity.Thread;
import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

import java.time.LocalDateTime;

public record ChannelMessageResponse(
        Long id,
        Long channelId,
        Long senderMemberId,
        String senderName,
        String content,
        LocalDateTime createdAt
) {

    public static ChannelMessageResponse from(Thread thread) {
        WorkspaceMember sender = thread.getCreatedBy();
        User user = sender.getUser();

        return new ChannelMessageResponse(
                thread.getId(),
                thread.getChannel().getId(),
                sender.getId(),
                resolveSenderName(user),
                thread.getContent(),
                thread.getCreatedAt()
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
