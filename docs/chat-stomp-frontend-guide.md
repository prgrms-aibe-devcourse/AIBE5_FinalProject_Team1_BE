# Chat STOMP Frontend Guide

프론트엔드에서 CodeDock 채팅 WebSocket을 연동할 때 사용하는 최소 규격이다.

## Connection

```text
ws://localhost:8080/ws
```

STOMP CONNECT에는 access token을 전달한다.

```ts
import { Client } from "@stomp/stompjs";

const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  reconnectDelay: 5000,
});

client.activate();
```

REST API와 WebSocket 요청 모두 인증 사용자 기준으로 처리한다. 프론트는 `X-User-Id`, `workspaceMemberId`, `senderMemberId`, `userId`, `senderName`을 사용자 식별값으로 보내지 않는다.

채널/스레드 topic 구독은 백엔드에서 workspace active member인지 검증한다. 권한이 없거나 payload 검증에 실패한 WebSocket 요청은 `/user/queue/errors`로 내려온다.

## Destinations

| Feature | Send | Subscribe |
| --- | --- | --- |
| Channel message | `/app/channels/{channelId}/messages` | `/topic/channels/{channelId}/events` |
| Thread reply | `/app/threads/{threadId}/replies` | `/topic/threads/{threadId}/events` |
| Typing | `/app/channels/{channelId}/typing` | `/topic/channels/{channelId}/typing` |
| Personal notification | - | `/user/queue/notifications` |
| Personal error | - | `/user/queue/errors` |

## Event Envelope

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

## Channel Events

```ts
client.subscribe(`/topic/channels/${channelId}/events`, (message) => {
  const event = JSON.parse(message.body) as ChatEvent<unknown>;

  if (event.type === "MESSAGE_CREATED") {
    const payload = event.payload as ChannelMessagePayload;
    // 메시지 목록에 추가
  }

  if (event.type === "MESSAGE_UPDATED") {
    const payload = event.payload as ChannelMessagePayload;
    // 메시지 내용 갱신
  }

  if (event.type === "MESSAGE_DELETED") {
    const payload = event.payload as ChannelMessagePayload;
    // 삭제 표시로 갱신
  }

  if (event.type === "REACTION_UPDATED") {
    const payload = event.payload as ReactionUpdatedPayload;
    // targetType + targetId 기준으로 리액션 갱신
  }
});
```

## Message

### STOMP Send

```ts
client.publish({
  destination: `/app/channels/${channelId}/messages`,
  body: JSON.stringify({
    content,
  }),
});
```

### REST Fallback

```http
POST /api/channels/{channelId}/messages
Authorization: Bearer {accessToken}
```

REST fallback으로 생성해도 백엔드는 `/topic/channels/{channelId}/events`로 `MESSAGE_CREATED`를 broadcast한다.

### Payload

```ts
type ChannelMessagePayload = {
  id: number;
  channelId: number;
  senderMemberId: number;
  senderName: string;
  content: string;
  createdAt: string;
  attachments: ThreadAttachmentPayload[];
};
```

## Thread Reply

### STOMP Send

```ts
client.publish({
  destination: `/app/threads/${threadId}/replies`,
  body: JSON.stringify({
    content,
  }),
});
```

### REST Fallback

```http
POST /api/threads/{threadId}/replies
Authorization: Bearer {accessToken}
```

REST fallback으로 생성해도 백엔드는 `/topic/threads/{threadId}/events`로 `THREAD_REPLY_CREATED`를 broadcast한다.

### Subscribe

```ts
client.subscribe(`/topic/threads/${threadId}/events`, (message) => {
  const event = JSON.parse(message.body) as ChatEvent<unknown>;

  if (event.type === "THREAD_REPLY_CREATED") {
    const payload = event.payload as ThreadReplyPayload;
    // 답글 목록에 추가
  }
});
```

```ts
type ThreadReplyPayload = {
  id: number;
  threadId: number;
  senderMemberId: number;
  senderName: string;
  content: string;
  createdAt: string;
};
```

## Typing

### Send

```ts
client.publish({
  destination: `/app/channels/${channelId}/typing`,
  body: JSON.stringify({
    typing: true,
  }),
});
```

입력을 멈추면 `typing: false`를 보낸다.

### Subscribe

```ts
client.subscribe(`/topic/channels/${channelId}/typing`, (message) => {
  const event = JSON.parse(message.body) as ChatEvent<TypingPayload>;
  const payload = event.payload;

  if (payload.workspaceMemberId !== currentWorkspaceMemberId) {
    // 타이핑 인디케이터 갱신
  }
});
```

```ts
type TypingPayload = {
  channelId: number;
  workspaceMemberId: number;
  senderName: string;
  typing: boolean;
};
```

`workspaceMemberId`와 `senderName`은 서버가 인증 사용자 기준으로 내려준다.

## Reaction

리액션은 REST로 토글하고, 변경 결과는 채널 event topic에서 받는다.

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

`targetType`은 `thread` 또는 `thread_reply`다. Oracle 문자셋 이슈를 피하려면 raw emoji보다 `like`, `smile` 같은 key를 우선 사용한다.

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

초기 렌더링 시 기존 리액션 집계는 아래 API로 가져온다.

```http
GET /api/channels/{channelId}/reactions
```

## Bookmark

```http
POST /api/channels/{channelId}/messages/{messageId}/bookmark
GET /api/workspaces/{workspaceId}/bookmarks
```

현재 백엔드 스키마는 메시지 북마크만 지원한다. 답글 북마크는 별도 마이그레이션이 필요하다.

## Personal Notification

```ts
client.subscribe("/user/queue/notifications", (message) => {
  const event = JSON.parse(message.body) as ChatEvent<PersonalNotificationPayload>;

  if (event.type === "NOTIFICATION_CREATED") {
    // 멘션 알림 표시
  }
});
```

```ts
type PersonalNotificationPayload = {
  workspaceId: number;
  channelId: number;
  threadId: number;
  threadReplyId: number | null;
  mentionedMemberId: number;
  message: string;
  createdAt: string;
};
```

## Personal Error

```ts
client.subscribe("/user/queue/errors", (message) => {
  const response = JSON.parse(message.body) as {
    success: false;
    code: string;
    message: string;
  };

  // SEND 실패 사유를 사용자에게 표시하거나 pending 메시지를 실패 처리
});
```

## Cleanup

채널 이동이나 컴포넌트 unmount 시 구독을 해제한다.

```ts
const channelSubscription = client.subscribe(
  `/topic/channels/${channelId}/events`,
  handleChannelEvent,
);

const typingSubscription = client.subscribe(
  `/topic/channels/${channelId}/typing`,
  handleTypingEvent,
);

channelSubscription.unsubscribe();
typingSubscription.unsubscribe();
```
