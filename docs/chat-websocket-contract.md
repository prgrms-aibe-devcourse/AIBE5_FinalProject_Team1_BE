# Chat WebSocket Contract

## Purpose

CodeDock 채팅에서 백엔드와 프론트가 동일하게 사용하는 WebSocket/STOMP destination과 event envelope 계약을 정의한다.

## WebSocket Endpoint

| Item | Value |
| --- | --- |
| WebSocket endpoint | `/ws` |
| Publish prefix | `/app` |
| Subscribe prefix | `/topic`, `/queue` |
| User destination prefix | `/user` |

STOMP `CONNECT` 요청에는 JWT access token을 전달한다.

```http
Authorization: Bearer {accessToken}
```

백엔드는 CONNECT 시점에 JWT를 검증하고, 이후 메시지/답글/typing 요청 작성자는 STOMP `Principal` 기준으로 판별한다. 따라서 WebSocket 요청 body에는 `workspaceMemberId`, `senderMemberId`, `userId`, `senderName`을 포함하지 않는다.

## Runtime Guards

- `/topic/channels/{channelId}/events`, `/topic/channels/{channelId}/typing`, `/topic/threads/{threadId}/events` 구독은 해당 workspace의 active member만 허용한다.
- STOMP `SEND`는 WebSocket 세션 기준 10초에 20건까지만 허용한다.
- `@MessageMapping` handler에서 발생한 요청 검증/비즈니스 오류는 `/user/queue/errors`로 현재 발신 세션에만 전달한다.
- CONNECT, SUBSCRIBE, rate limit처럼 inbound interceptor에서 거부된 요청은 STOMP ERROR 또는 연결 거부로 처리될 수 있다.
- 멘션 개인 알림은 메시지/답글 저장 트랜잭션이 커밋된 뒤 `/user/queue/notifications`로 전송한다.

## STOMP Destinations

| Feature | Client Send | Client Subscribe |
| --- | --- | --- |
| Channel message | `/app/channels/{channelId}/messages` | `/topic/channels/{channelId}/events` |
| Thread reply | `/app/threads/{threadId}/replies` | `/topic/threads/{threadId}/events` |
| Typing | `/app/channels/{channelId}/typing` | `/topic/channels/{channelId}/typing` |
| Personal notification | - | `/user/queue/notifications` |
| Personal error | - | `/user/queue/errors` |

## Event Envelope

모든 WebSocket broadcast 응답은 아래 형식을 사용한다.

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {}
}
```

## Event Types

| Type | Description |
| --- | --- |
| `MESSAGE_CREATED` | 채널 메시지 생성 |
| `MESSAGE_UPDATED` | 채널 메시지 수정 |
| `MESSAGE_DELETED` | 채널 메시지 삭제 |
| `THREAD_REPLY_CREATED` | 스레드 답글 생성 |
| `REACTION_UPDATED` | 메시지 또는 답글 리액션 변경 |
| `TYPING` | 입력 중 상태 변경 |
| `NOTIFICATION_CREATED` | 개인 알림 생성 |

## Channel Message

### Send

```text
/app/channels/{channelId}/messages
```

```json
{
  "content": "이번 PR 리뷰 부탁드립니다"
}
```

### Subscribe

```text
/topic/channels/{channelId}/events
```

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {
    "id": 10,
    "channelId": 1,
    "senderMemberId": 1,
    "senderName": "김재준",
    "content": "이번 PR 리뷰 부탁드립니다",
    "createdAt": "2026-06-08T10:30:00",
    "attachments": []
  }
}
```

REST 생성 fallback인 `POST /api/channels/{channelId}/messages`도 성공 시 같은 `MESSAGE_CREATED` event를 broadcast한다.

## Thread Reply

### Send

```text
/app/threads/{threadId}/replies
```

```json
{
  "content": "이 부분은 rate limit 추가가 필요해 보여요"
}
```

### Subscribe

```text
/topic/threads/{threadId}/events
```

```json
{
  "type": "THREAD_REPLY_CREATED",
  "payload": {
    "id": 21,
    "threadId": 10,
    "senderMemberId": 1,
    "senderName": "김재준",
    "content": "이 부분은 rate limit 추가가 필요해 보여요",
    "createdAt": "2026-06-08T10:35:00"
  }
}
```

REST 생성 fallback인 `POST /api/threads/{threadId}/replies`도 성공 시 같은 `THREAD_REPLY_CREATED` event를 broadcast한다.

## Typing

### Send

```text
/app/channels/{channelId}/typing
```

```json
{
  "typing": true
}
```

### Subscribe

```text
/topic/channels/{channelId}/typing
```

```json
{
  "type": "TYPING",
  "payload": {
    "channelId": 1,
    "workspaceMemberId": 1,
    "senderName": "김재준",
    "typing": true
  }
}
```

`workspaceMemberId`와 `senderName`은 클라이언트 요청값이 아니라 서버가 인증 사용자와 채널의 workspace context로 조회한 값이다.

## Reaction

리액션은 REST API로 토글하고, 변경 결과는 채널 event topic으로 전송한다.

```http
POST /api/channels/{channelId}/reactions/toggle
Authorization: Bearer {accessToken}
```

```json
{
  "targetType": "thread",
  "targetId": 100,
  "emoji": "like"
}
```

`targetType`은 `thread` 또는 `thread_reply`만 허용한다.

`emoji` 필드는 실제 이모지가 아니라 API/DB에서 사용하는 reaction key다. Oracle 문자셋 호환성을 위해 DB에는 raw emoji를 저장하지 않는다. 프론트는 화면 표시 시에만 reaction key를 실제 이모지로 매핑한다.

| Reaction key | Display |
| --- | --- |
| `like` | 👍 |
| `dislike` | 👎 |
| `heart` | ❤️ |
| `laugh` | 😂 |
| `smile` | 😄 |
| `surprised` | 😮 |
| `sad` | 😢 |
| `cry` | 😭 |
| `angry` | 😡 |
| `thinking` | 🤔 |
| `clap` | 👏 |
| `pray` | 🙏 |
| `eyes` | 👀 |
| `fire` | 🔥 |
| `rocket` | 🚀 |
| `party` | 🎉 |
| `check` | ✅ |
| `cross` | ❌ |
| `star` | ⭐ |
| `bulb` | 💡 |
| `bug` | 🐛 |
| `fix` | 🔧 |
| `memo` | 📝 |
| `coffee` | ☕ |

백엔드는 호환성을 위해 raw emoji가 들어와도 위 reaction key로 정규화해서 응답한다. 허용되지 않은 값은 `INVALID_INPUT`으로 거부한다.

```json
{
  "type": "REACTION_UPDATED",
  "payload": {
    "channelId": 1,
    "workspaceMemberId": 10,
    "targetType": "thread",
    "targetId": 100,
    "emoji": "like",
    "reacted": true,
    "count": 3
  }
}
```

초기 렌더링용 집계는 아래 API를 사용한다.

```http
GET /api/channels/{channelId}/reactions
```

## Bookmark

현재 MVP는 채널 메시지(thread) 북마크만 지원한다. 답글 북마크는 `bookmarks.thread_reply_id` 스키마가 없어 제외한다.

```http
POST /api/channels/{channelId}/messages/{messageId}/bookmark
GET /api/workspaces/{workspaceId}/bookmarks
```

## Mention And Notification

멘션 토큰은 `@` 뒤에 한글, 영문, 숫자, `.`, `_`, `-`가 오는 형식을 지원한다.

```text
@김재준 @user.name @dev-1
```

메시지/답글 저장 후 멘션 대상이 있으면 `Mention`을 저장하고 개인 알림을 전송한다.

```json
{
  "type": "NOTIFICATION_CREATED",
  "payload": {
    "workspaceId": 1,
    "channelId": 1,
    "threadId": 10,
    "threadReplyId": null,
    "mentionedMemberId": 2,
    "message": "새 멘션이 도착했습니다.",
    "createdAt": "2026-06-08T10:40:00"
  }
}
```

## Channel Policy

| Type | Created By | GitHub Repository | Deletable |
| --- | --- | --- | --- |
| `general` | workspace 생성 | 없음 | false |
| `custom` | 채널 생성 API | 없음 | true |
| `repository` | GitHub repository 연동 | 있음 | false |

## Notes

- 모든 broadcast 응답은 `ChatEventResponse<T>` envelope를 사용한다.
- WebSocket 메시지/답글/typing 요청은 인증 사용자 기준으로 workspace member를 조회한다.
- `/topic/channels/{channelId}/events`, `/topic/channels/{channelId}/typing`, `/topic/threads/{threadId}/events` 구독은 해당 workspace active member만 허용한다.
- WebSocket handler 예외는 `/user/queue/errors`로 `ApiResponse.fail(code, message)` 형태로 전달한다.
- Redis Pub/Sub은 아직 적용하지 않는다.
