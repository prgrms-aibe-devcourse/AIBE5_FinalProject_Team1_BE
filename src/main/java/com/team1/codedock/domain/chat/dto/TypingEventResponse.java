package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

public record TypingEventResponse(
        Long channelId,
        Long workspaceMemberId,
        String senderName,
        Boolean typing
) {
    // Server fills sender identity from the authenticated workspace member.
    public static TypingEventResponse of(Long channelId, WorkspaceMember sender, TypingEventRequest request) {
        return new TypingEventResponse(
                channelId,
                sender.getId(),
                resolveSenderName(sender.getUser()),
                request.typing()
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
