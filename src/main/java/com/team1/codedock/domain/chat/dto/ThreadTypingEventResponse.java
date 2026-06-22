package com.team1.codedock.domain.chat.dto;

import com.team1.codedock.domain.user.entity.User;
import com.team1.codedock.domain.workspace.entity.WorkspaceMember;

public record ThreadTypingEventResponse(
        Long threadId,
        Long workspaceMemberId,
        String senderName,
        Boolean typing
) {
    // 스레드 typing도 클라이언트가 보낸 이름을 믿지 않고 서버의 현재 멤버 정보로 구성함.
    public static ThreadTypingEventResponse of(Long threadId, WorkspaceMember sender, TypingEventRequest request) {
        return new ThreadTypingEventResponse(
                threadId,
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
