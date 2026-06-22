-- threads.client_message_id: 낙관적(pending) 전송 멱등 키.
-- FE가 생성해 전송하며, 같은 (channel, 작성자, client_message_id)면 동일 메시지로 간주함.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'THREADS'
      AND column_name = 'CLIENT_MESSAGE_ID';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE threads ADD (client_message_id VARCHAR2(64) NULL)';
    END IF;
END;
/

-- client_message_id가 있는 메시지에 대해서만 (channel_id, created_by_id, client_message_id) 유니크를 강제함.
-- 함수 기반 부분 인덱스: client_message_id가 NULL이면 세 키 모두 NULL이 되고, Oracle은 "모든 키가 NULL"인
-- 엔트리를 인덱스에 저장하지 않으므로 봇/레거시(NULL) 메시지는 제약에서 제외됨.
-- (단순 복합 유니크 인덱스는 NULL client_message_id 행들이 같은 channel/작성자에서 ORA-01452로 충돌함)
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE index_name = 'UX_THREADS_CLIENT_MSG';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_threads_client_msg ON threads ('
            || 'CASE WHEN client_message_id IS NULL THEN NULL ELSE channel_id END, '
            || 'CASE WHEN client_message_id IS NULL THEN NULL ELSE created_by_id END, '
            || 'client_message_id)';
    END IF;
END;
/

-- 멱등 조회(findFirstByChannel_IdAndCreatedBy_IdAndClientMessageId)는 메시지 전송마다 실행됨.
-- 위 함수 기반 유니크 인덱스는 CASE 표현식 키라 원시 컬럼 술어(client_message_id = ?)에 사용되지 않으므로,
-- 조회 성능을 위해 단일 컬럼 인덱스를 별도로 둠. Oracle은 단일 컬럼 인덱스에서 NULL을 저장하지 않아
-- 실제 값이 있는(비-NULL) 행만 인덱싱됨(UUID라 매우 선택적).
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_indexes
    WHERE index_name = 'IX_THREADS_CLIENT_MSG';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX ix_threads_client_msg ON threads (client_message_id)';
    END IF;
END;
/
