-- Oracle 11.2는 식별자 최대 30자. 시퀀스/PK/트리거 이름을 30자 이하로 유지한다.
CREATE SEQUENCE seq_ws_event_read_status START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspace_event_read_statuses (
    id                 NUMBER(19) NOT NULL,
    workspace_event_id NUMBER(19) NOT NULL,
    user_id            NUMBER(19) NOT NULL,
    CONSTRAINT pk_ws_event_read_status PRIMARY KEY (id),
    CONSTRAINT uq_event_read_status UNIQUE (workspace_event_id, user_id)
);

CREATE OR REPLACE TRIGGER trg_ws_event_read_status_bi
  BEFORE INSERT ON workspace_event_read_statuses
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_ws_event_read_status.NEXTVAL;
  END IF;
END;
/
