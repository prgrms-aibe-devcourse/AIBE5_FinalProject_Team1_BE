-- 기존 개발 DB에 채널이 0개인 워크스페이스가 있으면 프론트가 WebSocket 연결 조건을 못 탐.
-- 이미 채널이 하나라도 있는 워크스페이스는 건드리지 않고, 비어 있는 워크스페이스에만 기본 general 채널을 추가함.
INSERT INTO channels (
    id,
    workspace_id,
    github_repository_id,
    name,
    channel_type,
    is_deletable,
    description,
    created_at,
    updated_at
)
SELECT
    seq_channels.NEXTVAL,
    w.id,
    NULL,
    'general',
    'general',
    0,
    '기본 워크스페이스 채널',
    SYSTIMESTAMP,
    SYSTIMESTAMP
FROM workspaces w
WHERE NOT EXISTS (
    SELECT 1
    FROM channels c
    WHERE c.workspace_id = w.id
);
