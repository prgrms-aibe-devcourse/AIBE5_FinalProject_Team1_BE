# Repository Channel and Mention API 계약서

## 목적

이 문서는 아래 백엔드 API 계약을 정의한다.

- GitHub 레포지토리를 워크스페이스에 연동할 때 `repository` 타입 채널을 생성한다.
- 저장된 멘션 알림을 멘션 목록에서 삭제한다.

모든 API는 인증된 사용자만 호출할 수 있다. 백엔드는 JWT access token에서 현재 사용자를 확인한다. 클라이언트는 권한 판단을 위해 `X-User-Id`를 보내면 안 된다.

```http
Authorization: Bearer {accessToken}
```

## Repository 채널 규칙

워크스페이스에 연동된 GitHub 레포지토리 1개는 반드시 repository 채널 1개와 연결된다.

백엔드 동작 규칙:

- GitHub 레포지토리 row가 없으면 새로 생성한다.
- 같은 워크스페이스에 동일한 GitHub 레포지토리 row가 이미 있으면 metadata를 갱신한다.
- 해당 GitHub 레포지토리의 repository 채널이 이미 있으면 기존 채널을 반환한다.
- 해당 GitHub 레포지토리의 repository 채널이 없으면 새로 생성한다.
- repository 채널은 `channelType = "repository"`로 생성된다.
- repository 채널은 `isDeletable = false`로 생성된다.
- repository 채널명은 기본적으로 레포지토리 이름을 사용한다.
- 같은 워크스페이스에 동일한 채널명이 이미 있으면 백엔드는 `-{owner}` 또는 `-repo-{githubRepoId}` suffix를 붙여 안전한 채널명을 만든다.
- repository 채널명은 `channels.name` 컬럼이 `VARCHAR2(120)`이므로 최대 120자까지만 허용한다.

이 규칙은 같은 GitHub 레포지토리에 대해 repository 채널이 중복 생성되는 것을 방지한다.

## Repository 채널 생성 API

### Endpoint

```http
POST /api/workspaces/{workspaceId}/github/repositories
POST /api/v1/workspaces/{workspaceId}/github/repositories
```

### 권한

워크스페이스 멤버 중 `owner` 또는 `admin` 권한을 가진 사용자만 repository 채널을 생성하거나 연동할 수 있다.

### Request Body

```json
{
  "githubRepoId": "123456789",
  "owner": "team1",
  "name": "codedock",
  "fullName": "team1/codedock",
  "url": "https://github.com/team1/codedock",
  "description": "CodeDock backend repository",
  "isPrivate": true,
  "defaultBranch": "main"
}
```

### Request Fields

| 필드 | 필수 여부 | 설명 |
| --- | --- | --- |
| `githubRepoId` | 필수 | GitHub 레포지토리 id. 최대 100자. |
| `owner` | 필수 | 레포지토리 owner login 또는 organization 이름. 최대 100자. |
| `name` | 필수 | 레포지토리 이름. 최대 120자. |
| `fullName` | 필수 | 전체 레포지토리 이름. 일반적으로 `{owner}/{repo}` 형식. 최대 255자. |
| `url` | 필수 | GitHub 레포지토리 HTML URL. |
| `description` | 선택 | 레포지토리 설명. |
| `isPrivate` | 필수 | private 레포지토리 여부. |
| `defaultBranch` | 선택 | 기본 브랜치 이름. 최대 255자. |

### Success Response

Status: `201 Created`

```json
{
  "success": true,
  "data": {
    "id": 40,
    "workspaceId": 10,
    "githubRepositoryId": 30,
    "name": "codedock",
    "channelType": "repository",
    "isDeletable": false,
    "description": "CodeDock backend repository",
    "lastMessage": null,
    "lastMessageAt": null,
    "messageCount": 0,
    "unreadCount": 0
  }
}
```

### Error Cases

| 상황 | 응답 |
| --- | --- |
| 인증되지 않은 사용자 | `401 C002` |
| 활성 워크스페이스 멤버가 아님 | `403 C003` |
| `owner` 또는 `admin` 권한이 아님 | `403 C003` |
| 워크스페이스가 존재하지 않음 | `404 W001` |
| 요청값 검증 실패 | `400 C001` |
| repository 채널명을 유일하게 만들 수 없음 | `409 C006` |

## 기존 GitHub Connect API

기존 GitHub connect API도 GitHub 레포지토리를 연동한 뒤 repository 채널을 생성하거나 기존 채널을 재사용한다.

### Endpoint

```http
POST /api/workspaces/{workspaceId}/github
POST /api/v1/workspaces/{workspaceId}/github
```

### 권한

워크스페이스 멤버 중 `owner` 또는 `admin` 권한을 가진 사용자만 이 API로 레포지토리를 연결할 수 있다.

### Request Body

```json
{
  "owner": "team1",
  "repo": "codedock"
}
```

### Success Response

```json
{
  "success": true,
  "data": {
    "id": 30,
    "channelId": 40,
    "owner": "team1",
    "name": "codedock",
    "fullName": "team1/codedock",
    "url": "https://github.com/team1/codedock",
    "defaultBranch": "main",
    "isPrivate": true
  }
}
```

`channelId`는 연결된 GitHub 레포지토리에 대해 생성되었거나 재사용된 repository 채널 id다.

## 멘션 삭제 API

멘션 삭제는 `mentions` 테이블에 저장된 멘션 목록 row를 삭제한다.

이 API는 WebSocket 알림 삭제 API가 아니다. `/user/queue/notifications`는 실시간 payload stream이며, 그 자체가 저장되는 테이블은 없다. 화면에 남는 멘션 알림 목록은 `mentions` 테이블 기준이다.

### Endpoint

```http
DELETE /api/mentions/{mentionId}
```

### 권한

멘션을 받은 사용자 본인만 해당 멘션을 삭제할 수 있다.

### Success Response

Status: `200 OK`

```json
{
  "success": true
}
```

### Error Cases

| 상황 | 응답 |
| --- | --- |
| 인증되지 않은 사용자 | `401 C002` |
| 멘션이 존재하지 않음 | `404 C004` |
| 다른 사용자의 멘션을 삭제하려고 함 | `403 C003` |

### Frontend Notes

- 삭제 성공 시 프론트는 로컬 멘션 목록에서 해당 항목을 제거한다.
- 멘션 삭제 시 별도의 WebSocket event는 발행하지 않는다.
- 여러 탭이 열려 있는 경우 각 탭은 필요 시 멘션 목록을 다시 조회하거나 새로고침한다.
