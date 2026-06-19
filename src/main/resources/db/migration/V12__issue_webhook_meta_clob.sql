-- V12: thread_attachments.meta 컬럼을 CLOB으로 확장
-- (Webhook 이슈 첨부 시 JSON 메타데이터가 100자를 초과하는 경우 대응)

ALTER TABLE thread_attachments ADD (meta_clob CLOB NULL);

UPDATE thread_attachments SET meta_clob = meta WHERE meta IS NOT NULL;

ALTER TABLE thread_attachments DROP COLUMN meta;

ALTER TABLE thread_attachments RENAME COLUMN meta_clob TO meta;
