DECLARE
    v_count NUMBER;
BEGIN
    -- 마이그레이션 파일은 이슈 번호가 아니라 마지막 버전 다음 순번으로 생성함.
    -- 공유 개발 DB에서 V19가 이미 적용 이력으로 남아 실행되지 않는 경우가 있어 V20에서 다시 보정함.
    -- 컬럼이 이미 있으면 아무 작업도 하지 않으므로 새 DB와 기존 DB 모두 안전하게 실행 가능함.
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
    -- display_order 컬럼이 뒤늦게 보정된 DB에서도 정렬 조회 인덱스를 보장함.
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE table_name = 'CHANNELS'
      AND index_name = 'IX_CHANNELS_DISPLAY_ORDER';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_channels_display_order ON channels (workspace_id, display_order, id)';
    END IF;
END;
/
