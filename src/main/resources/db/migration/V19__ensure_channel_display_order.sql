DECLARE
    v_count NUMBER;
BEGIN
    -- 마이그레이션 파일은 이슈 번호가 아니라 마지막 버전 다음 순번으로 생성함.
    -- 예: V18 다음 보정이면 V19, V19 다음 변경이면 V20 형식으로 추가함.
    -- 공유 개발 DB에서 V18 적용 이력과 실제 스키마가 어긋난 경우를 보정함.
    -- Flyway는 성공 처리된 버전을 다시 실행하지 않으므로 다음 순번 V19에서 컬럼 존재 여부를 다시 확인함.
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
