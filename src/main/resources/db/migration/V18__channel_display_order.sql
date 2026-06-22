DECLARE
    v_count NUMBER;
BEGIN
    -- 채널 순서를 서버에 저장하기 위한 컬럼임.
    -- 개발 DB에 수동 반영된 경우 ORA-01430을 피하려고 존재 여부를 먼저 확인함.
    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'CHANNELS'
      AND column_name = 'DISPLAY_ORDER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE channels ADD (display_order NUMBER(10) DEFAULT 0 NOT NULL)';
    END IF;
END;
/

MERGE INTO channels c
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY workspace_id
               ORDER BY created_at ASC, id ASC
           ) - 1 AS ordered_index
    FROM channels
) ordered
ON (c.id = ordered.id)
WHEN MATCHED THEN
    UPDATE SET c.display_order = ordered.ordered_index;

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE index_name = 'IX_CHANNELS_DISPLAY_ORDER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_channels_display_order ON channels (workspace_id, display_order, id)';
    END IF;
END;
/
