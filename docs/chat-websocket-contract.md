# Chat WebSocket Contract

## 1. Purpose

CodeDock 채팅 기능에서 백엔드와 프론트엔드가 동일한 WebSocket/STOMP destination과 이벤트 응답 형식을 사용하기 위한 계약 문서입니다.

이 문서는 채널 메시지, 스레드 답글, typing, 개인 알림 기능의 WebSocket 통신 규칙을 정의합니다.

## 2. WebSocket Endpoint

| Item | Value |
| --- | --- |
| WebSocket endpoint | `/ws` |
| Publish prefix | `/app` |
| Subscribe prefix | `/topic`, `/queue` |
| User destination prefix | `/user` |

## 3. STOMP Destinations

| Feature | Client Send | Client Subscribe |
| --- | --- | --- |
| Channel message | `/app/channels/{channelId}/messages` | `/topic/channels/{channelId}/events` |
| Thread reply | `/app/threads/{threadId}/replies` | `/topic/threads/{threadId}/events` |
| Typing | `/app/channels/{channelId}/typing` | `/topic/channels/{channelId}/typing` |
| Personal notification | - | `/user/queue/notifications` |

## 4. Event Envelope

모든 WebSocket broadcast 응답은 동일한 envelope 형식을 사용합니다.

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {}
}
```

## 5. Event Types

| Type | Description |
| --- | --- |
| `MESSAGE_CREATED` | 채널 메시지 생성 |
| `MESSAGE_UPDATED` | 채널 메시지 수정 |
| `MESSAGE_DELETED` | 채널 메시지 삭제 |
| `THREAD_REPLY_CREATED` | 스레드 답글 생성 |
| `REACTION_UPDATED` | 메시지 또는 답글 리액션 변경 |
| `TYPING` | 입력 중 상태 변경 |
| `NOTIFICATION_CREATED` | 개인 알림 생성 |

## 6. Channel Message

### Send

Destination:

```text
/app/channels/{channelId}/messages
```

Request:

```json
{
  "workspaceMemberId": 1,
  "content": "이번 PR 리뷰 부탁드립니다."
}
```

### Subscribe

Destination:

```text
/topic/channels/{channelId}/events
```

Response:

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {
    "id": 10,
    "channelId": 1,
    "workspaceMemberId": 1,
    "senderName": "김자바",
    "content": "이번 PR 리뷰 부탁드립니다.",
    "threadType": "user_message",
    "createdAt": "2026-06-08T10:30:00"
  }
}
```

## 7. Thread Reply

### Send

Destination:

```text
/app/threads/{threadId}/replies
```

Request:

```json
{
  "workspaceMemberId": 1,
  "content": "이 부분은 rate limit 추가가 필요해 보여요."
}
```

### Subscribe

Destination:

```text
/topic/threads/{threadId}/events
```

Response:

```json
{
  "type": "THREAD_REPLY_CREATED",
  "payload": {
    "id": 21,
    "threadId": 10,
    "workspaceMemberId": 1,
    "senderName": "김자바",
    "content": "이 부분은 rate limit 추가가 필요해 보여요.",
    "createdAt": "2026-06-08T10:35:00"
  }
}
```

## 8. Typing

### Send

Destination:

```text
/app/channels/{channelId}/typing
```

Request:

```json
{
  "workspaceMemberId": 1,
  "senderName": "김자바",
  "typing": true
}
```

### Subscribe

Destination:

```text
/topic/channels/{channelId}/typing
```

Response:

```json
{
  "type": "TYPING",
  "payload": {
    "channelId": 1,
    "workspaceMemberId": 1,
    "senderName": "김자바",
    "typing": true
  }
}
```

## 9. Personal Notification

### Subscribe

Destination:

```text
/user/queue/notifications
```

Response:

```json
{
  "type": "NOTIFICATION_CREATED",
  "payload": {
    "workspaceId": 1,
    "channelId": 1,
    "threadId": 10,
    "mentionedMemberId": 2,
    "message": "김자바님이 회원님을 멘션했습니다."
  }
}
```

## 10. Development Notes

- JWT 인증이 완료되기 전 초기 구현에서는 request DTO에 `workspaceMemberId`를 임시로 포함할 수 있습니다.
- JWT 인증이 완료된 뒤에는 인증된 사용자와 workspace context로 `workspaceMemberId`를 해석합니다.
- 채널 메시지는 `Thread`로 저장합니다.
- 스레드 답글은 `ThreadReply`로 저장합니다.
- 모든 broadcast 응답은 `type`, `payload` envelope 형식을 유지합니다.
- 채널 이벤트 구독자는 `type` 값으로 UI 갱신 로직을 분기합니다.
- Reaction, mention, bookmark, read status, Redis Pub/Sub, WebSocket 인증은 이후 확장 범위입니다.
