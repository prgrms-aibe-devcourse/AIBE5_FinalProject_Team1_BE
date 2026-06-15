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
STOMP CONNECT 요청에는 JWT access token을 `Authorization: Bearer {accessToken}` 형식으로 전달합니다.

```ts
import { Client } from "@stomp/stompjs";

const accessToken = getAccessToken();

const client = new Client({
  brokerURL: "ws://localhost:8080/ws",
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
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

채팅 REST API도 동일하게 JWT access token을 사용합니다. 프론트는 `X-User-Id` 헤더나 요청 body의 `workspaceMemberId`로 현재 사용자를 넘기지 않습니다.

```http
Authorization: Bearer {accessToken}
```

## 2. Destination Summary

| Feature | Client Send | Client Subscribe |
| --- | --- | --- |
| Channel message | `/app/channels/{channelId}/messages` | `/topic/channels/{channelId}/events` |
| Typing | `/app/channels/{channelId}/typing` | `/topic/channels/{channelId}/typing` |
| Reaction | `POST /api/channels/{channelId}/reactions/toggle` | `/topic/channels/{channelId}/events` |
| Reaction summary | `GET /api/channels/{channelId}/reactions` | - |
| Thread reply | `/app/threads/{threadId}/replies` | `/topic/threads/{threadId}/events` |
| Personal notification | - | `/user/queue/notifications` |

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
  "content": "메시지 내용"
}
```

Example:

```ts
client.publish({
  destination: `/app/channels/${channelId}/messages`,
  body: JSON.stringify({
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
    "createdAt": "2026-06-09T10:30:00",
    "attachments": []
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
  attachments: ThreadAttachmentPayload[];
};

type ThreadAttachmentPayload = {
  id: number;
  attachmentType: string;
  type: string;
  targetId: number | null;
  url: string | null;
  title: string | null;
  detail: string | null;
  meta: string | null;
  previewUrl: string | null;
  mimeType: string | null;
  fileSize: number | null;
  size: number | null;
  createdAt: string;
};
```

## 6. Thread Reply

### Send

Destination:

```text
/app/threads/{threadId}/replies
```

Payload:

```json
{
  "content": "답글 내용"
}
```

Example:

```ts
client.publish({
  destination: `/app/threads/${threadId}/replies`,
  body: JSON.stringify({
    content,
  }),
});
```

### Receive

Subscribe:

```text
/topic/threads/{threadId}/events
```

Event:

```json
{
  "type": "THREAD_REPLY_CREATED",
  "payload": {
    "id": 200,
    "threadId": 100,
    "senderMemberId": 10,
    "senderName": "김재준",
    "content": "답글 내용",
    "createdAt": "2026-06-09T10:35:00"
  }
}
```

TypeScript payload:

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

## 7. Typing

### Send

Destination:

```text
/app/channels/{channelId}/typing
```

Payload:

```json
{
  "senderName": "김재준",
  "typing": true
}
```

Example:

```ts
client.publish({
  destination: `/app/channels/${channelId}/typing`,
  body: JSON.stringify({
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

## 8. Reaction

리액션은 REST API로 토글하고, 변경 결과는 WebSocket channel event로 수신합니다.

### Toggle

Endpoint:

```http
POST /api/channels/{channelId}/reactions/toggle
Authorization: Bearer {accessToken}
```

Payload:

```json
{
  "targetType": "thread",
  "targetId": 100,
  "emoji": "👍"
}
```

`workspaceMemberId`는 요청 body에 포함하지 않습니다. 백엔드는 JWT 인증 사용자와 대상 채널의 workspace를 기준으로 활성 `WorkspaceMember`를 조회합니다.

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

### Summary

채널에 처음 들어왔을 때 기존 메시지와 답글에 달린 리액션 개수를 표시하기 위한 조회 API입니다.

Endpoint:

```http
GET /api/channels/{channelId}/reactions
```

Response:

```json
{
  "success": true,
  "data": [
    {
      "targetType": "thread",
      "targetId": 100,
      "emoji": "👍",
      "count": 3
    },
    {
      "targetType": "thread_reply",
      "targetId": 200,
      "emoji": "😂",
      "count": 2
    }
  ]
}
```

TypeScript payload:

```ts
type ReactionSummaryPayload = {
  targetType: "thread" | "thread_reply";
  targetId: number;
  emoji: string;
  count: number;
};
```

프론트 초기 렌더링 흐름:

```text
1. 채널 메시지 목록 조회
2. GET /api/channels/{channelId}/reactions 호출
3. targetType + targetId 기준으로 메시지/답글에 리액션 개수 매핑
4. 이후 변경분은 REACTION_UPDATED WebSocket 이벤트로 반영
```

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

## 9. Personal Notification

개인 알림은 `/user` destination으로 수신합니다. 백엔드는 `convertAndSendToUser(..., "/queue/notifications", ...)`로 전송하므로 프론트 구독 주소는 `/user/queue/notifications`입니다.

### Subscribe

```text
/user/queue/notifications
```

Event:

```json
{
  "type": "NOTIFICATION_CREATED",
  "payload": {
    "workspaceId": 1,
    "channelId": 1,
    "threadId": 100,
    "threadReplyId": null,
    "mentionedMemberId": 10,
    "message": "김재준님이 회원님을 멘션했습니다.",
    "createdAt": "2026-06-09T10:40:00"
  }
}
```

TypeScript payload:

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

## 10. Cleanup

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

## 11. Current Limitations

- REST 채팅 API는 `Authorization: Bearer {accessToken}` 기준으로 현재 사용자를 식별합니다.
- Reaction REST 요청 body에는 `workspaceMemberId`를 전달하지 않습니다.
- WebSocket 메시지/답글/typing 요청 body에는 `workspaceMemberId`, `senderMemberId`, `userId`를 전달하지 않습니다.
- Redis Pub/Sub은 아직 적용하지 않습니다.
- 첨부파일은 메시지 메타데이터로 저장되며, 실제 바이너리 파일 업로드/스토리지 연동은 별도 흐름으로 다룹니다.
- 운영 배포 환경에서는 WebSocket URL과 CORS origin을 환경에 맞게 조정해야 합니다.
