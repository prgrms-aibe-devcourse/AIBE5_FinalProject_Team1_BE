package com.team1.codedock.domain.chat.dto;

public record TypingEventResponse(
        Long channelId,
        Long workspaceMemberId,
        String senderName,
        Boolean typing
) {
    //서버가 채널 연결 response payload

    public static TypingEventResponse of(Long channelId, TypingEventRequest request) {
        return new TypingEventResponse(
                channelId,
                request.workspaceMemberId(),
                request.senderName(),
                request.typing()
        );
    }
}
