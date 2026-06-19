DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'INVITATIONS'
      AND column_name = 'INVITED_POSITION';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE invitations ADD (invited_position VARCHAR2(100) NULL)';
    END IF;
END;
/