package com.team1.codedock.domain.chat.event;

/**
 * 채팅 메시지가 생성될 때 Kafka로 발행되는 이벤트.
 * 소비 측에서 저장/검색 인덱싱/알림/통계 등 후처리에 사용한다.
 */
public record ChatMessageEvent(
        Long messageId,
        Long channelId,
        Long workspaceId,
        Long senderMemberId,
        String senderName,
        String content,
        String createdAt
) {
}
