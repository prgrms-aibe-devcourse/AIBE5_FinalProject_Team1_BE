# Repository Channel and Mention API

## Purpose

This document defines the backend contract for:

- Creating a repository-type channel when a GitHub repository is linked to a workspace.
- Deleting a stored mention notification from the mention list.

All APIs require an authenticated user. The backend resolves the current user from the JWT access token. Clients must not send `X-User-Id` as an authority source.

```http
Authorization: Bearer {accessToken}
```

## Repository Channel Rule

One linked GitHub repository must have exactly one repository channel in a workspace.

Backend behavior:

- If the GitHub repository row does not exist, the backend creates it.
- If the GitHub repository row already exists in the workspace, the backend refreshes its metadata.
- If the repository channel already exists for that GitHub repository, the backend returns the existing channel.
- If the repository channel does not exist, the backend creates one.
- Repository channels are created with `channelType = "repository"`.
- Repository channels are created with `isDeletable = false`.

This rule prevents duplicate repository channels for the same GitHub repository.

## Create Repository Channel

### Endpoint

```http
POST /api/workspaces/{workspaceId}/github/repositories
POST /api/v1/workspaces/{workspaceId}/github/repositories
```

### Permission

Only workspace members with `owner` or `admin` authority can create or link repository channels.

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

| Field | Required | Description |
| --- | --- | --- |
| `githubRepoId` | Yes | GitHub repository id. Max 100 chars. |
| `owner` | Yes | Repository owner login or organization name. Max 100 chars. |
| `name` | Yes | Repository name. Max 150 chars. |
| `fullName` | Yes | Full repository name, usually `{owner}/{repo}`. Max 255 chars. |
| `url` | Yes | GitHub repository HTML URL. |
| `description` | No | Repository description. |
| `isPrivate` | Yes | Whether the GitHub repository is private. |
| `defaultBranch` | No | Default branch name. Max 255 chars. |

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

| Case | Result |
| --- | --- |
| Not authenticated | `401 C002` |
| User is not active workspace member | `403 C003` |
| User is not `owner` or `admin` | `403 C003` |
| Workspace does not exist | `404 W001` |
| Request validation fails | `400 C001` |

## Existing GitHub Connect API

The existing GitHub connect API also creates or reuses the repository channel after linking the GitHub repository.

### Endpoint

```http
POST /api/workspaces/{workspaceId}/github
POST /api/v1/workspaces/{workspaceId}/github
```

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

`channelId` is the repository channel id created or reused for the connected repository.

## Mention Delete API

Mention deletion removes a stored row from the `mentions` list.

This is not a WebSocket notification delete API. `/user/queue/notifications` is a real-time payload stream and is not stored by itself. The persistent notification list for mentions is based on the `mentions` table.

### Endpoint

```http
DELETE /api/mentions/{mentionId}
```

### Permission

Only the user who received the mention can delete it.

### Success Response

Status: `200 OK`

```json
{
  "success": true
}
```

### Error Cases

| Case | Result |
| --- | --- |
| Not authenticated | `401 C002` |
| Mention does not exist | `404 C004` |
| Mention belongs to another member | `403 C003` |

### Frontend Notes

- After delete succeeds, remove the item from the local mention list.
- No WebSocket event is emitted for mention deletion.
- If the user has multiple tabs open, each tab should refresh or refetch the mention list when needed.

