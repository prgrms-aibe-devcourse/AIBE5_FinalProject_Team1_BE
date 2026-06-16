DECLARE
    v_count NUMBER;
BEGIN
    -- 개발 DB에 VERSION 컬럼은 이미 있지만 Flyway 이력에는 V9가 없을 수 있음
    -- 컬럼이 이미 있으면 ORA-01430 방지를 위해 ALTER TABLE을 건너뜀
    -- 이 방식이 계속 문제되면 V9를 제거하고 다음 버전 마이그레이션으로 옮기는 리팩터링 필요함
    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'WORKSPACE_MEMBERS'
      AND column_name = 'VERSION';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE workspace_members ADD (version NUMBER(19) DEFAULT 0 NOT NULL)';
    END IF;
END;
/
