package com.team1.codedock.domain.chat.dto;

public record TypingEventResponse(
        Long channelId,
        Long workspaceMemberId,
        String senderName,
        Boolean typing
) {
    // 서버가 인증 멤버 id를 채워서 내려주는 typing 이벤트 payload임
    public static TypingEventResponse of(Long channelId, Long workspaceMemberId, TypingEventRequest request) {
        return new TypingEventResponse(
                channelId,
                workspaceMemberId,
                request.senderName(),
                request.typing()
        );
    }
}
