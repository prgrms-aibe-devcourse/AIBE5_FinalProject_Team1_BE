CREATE SEQUENCE seq_workspace_event_read_statuses START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspace_event_read_statuses (
    id                 NUMBER(19) NOT NULL,
    workspace_event_id NUMBER(19) NOT NULL,
    user_id            NUMBER(19) NOT NULL,
    CONSTRAINT pk_workspace_event_read_statuses PRIMARY KEY (id),
    CONSTRAINT uq_event_read_status UNIQUE (workspace_event_id, user_id)
);

CREATE OR REPLACE TRIGGER trg_workspace_event_read_statuses_bi
  BEFORE INSERT ON workspace_event_read_statuses
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_workspace_event_read_statuses.NEXTVAL;
  END IF;
END;
/
