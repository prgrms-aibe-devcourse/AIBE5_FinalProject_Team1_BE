# Chat STOMP Frontend Guide

프론트엔드에서 CodeDock 채팅 WebSocket 기능을 연동하기 위한 가이드입니다.

이 문서는 현재 채팅 WebSocket 계약과 백엔드 구현 기준으로 STOMP 연결, 구독 destination, 전송 payload, 수신 event envelope를 정리합니다.

## 1. Connection

WebSocket endpoint:

```text
/ws
```

Local backend 기준 WebSocket URL:

```text
ws://localhost:8080/ws
```

현재 백엔드는 SockJS fallback을 사용하지 않습니다. 프론트에서는 `@stomp/stompjs`의 `brokerURL` 방식으로 연결합니다.

```ts
import { Client } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  reconnectDelay: 5000,
  onConnect: () => {
    console.log("STOMP connected");
  },
  onStompError: (frame) => {
    console.error("STOMP error", frame);
  },
});

client.activate();
```

## 2. Destination Summary

| Feature | Client Send | Client Subscribe |
| --- | --- | --- |
| Channel message | `/app/channels/{channelId}/messages` | `/topic/channels/{channelId}/events` |
| Typing | `/app/channels/{channelId}/typing` | `/topic/channels/{channelId}/typing` |
| Reaction | `POST /api/channels/{channelId}/reactions/toggle` | `/topic/channels/{channelId}/events` |
| Thread reply | `/app/threads/{threadId}/replies` | `/topic/threads/{threadId}/events` |
| Personal notification | - | `/user/queue/notifications` |

> `Thread reply`, `Personal notification`은 추후 구현 범위입니다.
> `Reaction`은 리액션 토글 PR 머지 후 사용할 규격입니다.

## 3. Event Envelope

모든 WebSocket broadcast 응답은 아래 형식을 사용합니다.

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {}
}
```

프론트는 `type` 값으로 이벤트를 분기합니다.

```ts
type ChatEventType =
  | "MESSAGE_CREATED"
  | "MESSAGE_UPDATED"
  | "MESSAGE_DELETED"
  | "THREAD_REPLY_CREATED"
  | "REACTION_UPDATED"
  | "TYPING"
  | "NOTIFICATION_CREATED";

type ChatEvent<T> = {
  type: ChatEventType;
  payload: T;
};
```

## 4. Channel Events Subscribe

채널 메시지 생성, 리액션 변경 등 채널 단위 이벤트는 아래 topic을 구독합니다.

```text
/topic/channels/{channelId}/events
```

예시:

```ts
client.subscribe(`/topic/channels/${channelId}/events`, (message) => {
  const event = JSON.parse(message.body) as ChatEvent<unknown>;

  if (event.type === "MESSAGE_CREATED") {
    const payload = event.payload as ChannelMessagePayload;
    // 메시지 목록에 payload 추가
  }

  if (event.type === "REACTION_UPDATED") {
    const payload = event.payload as ReactionUpdatedPayload;
    // targetId 기준으로 리액션 상태 갱신
  }
});
```

## 5. Channel Message

### Send

Destination:

```text
/app/channels/{channelId}/messages
```

Payload:

```json
{
  "senderMemberId": 1,
  "content": "메시지 내용"
}
```

Example:

```ts
client.publish({
  destination: `/app/channels/${channelId}/messages`,
  body: JSON.stringify({
    senderMemberId: currentWorkspaceMemberId,
    content,
  }),
});
```

### Receive

Subscribe:

```text
/topic/channels/{channelId}/events
```

Event:

```json
{
  "type": "MESSAGE_CREATED",
  "payload": {
    "id": 100,
    "channelId": 1,
    "senderMemberId": 10,
    "senderName": "김재준",
    "content": "메시지 내용",
    "createdAt": "2026-06-09T10:30:00"
  }
}
```

TypeScript payload:

```ts
type ChannelMessagePayload = {
  id: number;
  channelId: number;
  senderMemberId: number;
  senderName: string;
  content: string;
  createdAt: string;
};
```

## 6. Typing

### Send

Destination:

```text
/app/channels/{channelId}/typing
```

Payload:

```json
{
  "workspaceMemberId": 1,
  "senderName": "김재준",
  "typing": true
}
```

Example:

```ts
client.publish({
  destination: `/app/channels/${channelId}/typing`,
  body: JSON.stringify({
    workspaceMemberId: currentWorkspaceMemberId,
    senderName: currentUserName,
    typing: true,
  }),
});
```

입력이 멈췄을 때는 `typing: false`를 보냅니다.

### Receive

Subscribe:

```text
/topic/channels/{channelId}/typing
```

Event:

```json
{
  "type": "TYPING",
  "payload": {
    "channelId": 1,
    "workspaceMemberId": 10,
    "senderName": "김재준",
    "typing": true
  }
}
```

TypeScript payload:

```ts
type TypingPayload = {
  channelId: number;
  workspaceMemberId: number;
  senderName: string;
  typing: boolean;
};
```

프론트에서는 자기 자신이 보낸 typing 이벤트는 화면 표시에서 제외하는 것이 좋습니다.

```ts
if (payload.workspaceMemberId !== currentWorkspaceMemberId && payload.typing) {
  // "김재준님이 입력 중..." 표시
}
```

## 7. Reaction

리액션은 REST API로 토글하고, 변경 결과는 WebSocket channel event로 수신합니다.

### Toggle

Endpoint:

```http
POST /api/channels/{channelId}/reactions/toggle
```

Payload:

```json
{
  "workspaceMemberId": 1,
  "targetType": "thread",
  "targetId": 100,
  "emoji": "👍"
}
```

`targetType`:

| Value | Description |
| --- | --- |
| `thread` | 채널 메시지 |
| `thread_reply` | 스레드 답글 |

Response:

```json
{
  "success": true,
  "data": {
    "channelId": 1,
    "workspaceMemberId": 10,
    "targetType": "thread",
    "targetId": 100,
    "emoji": "👍",
    "reacted": true,
    "count": 3
  }
}
```

`reacted` 의미:

| Value | Description |
| --- | --- |
| `true` | 이번 요청으로 리액션이 추가됨 |
| `false` | 이번 요청으로 리액션이 취소됨 |

### Receive

Subscribe:

```text
/topic/channels/{channelId}/events
```

Event:

```json
{
  "type": "REACTION_UPDATED",
  "payload": {
    "channelId": 1,
    "workspaceMemberId": 10,
    "targetType": "thread",
    "targetId": 100,
    "emoji": "👍",
    "reacted": true,
    "count": 3
  }
}
```

TypeScript payload:

```ts
type ReactionUpdatedPayload = {
  channelId: number;
  workspaceMemberId: number;
  targetType: "thread" | "thread_reply";
  targetId: number;
  emoji: string;
  reacted: boolean;
  count: number;
};
```

## 8. Cleanup

채널 이동 또는 컴포넌트 unmount 시에는 구독을 해제합니다.

```ts
const channelEventsSubscription = client.subscribe(
  `/topic/channels/${channelId}/events`,
  handleChannelEvent,
);

const typingSubscription = client.subscribe(
  `/topic/channels/${channelId}/typing`,
  handleTypingEvent,
);

channelEventsSubscription.unsubscribe();
typingSubscription.unsubscribe();
```

## 9. Current Limitations

- JWT WebSocket 인증은 아직 적용하지 않습니다.
- 현재 메시지/typing 요청은 `workspaceMemberId` 또는 `senderMemberId`를 payload로 전달합니다.
- Redis Pub/Sub은 아직 적용하지 않습니다.
- 메시지 목록 조회, 답글 저장/조회, 읽음 처리, 멘션, 북마크는 별도 구현 범위입니다.
- 운영 배포 환경에서는 WebSocket URL과 CORS origin을 환경에 맞게 조정해야 합니다.
