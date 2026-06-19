-- ============================================================
-- V12: workspace_events + workspaces.last_activity_at
-- ============================================================

-- workspaces 테이블에 last_activity_at 컬럼 추가
ALTER TABLE workspaces ADD (last_activity_at TIMESTAMP NULL);

CREATE SEQUENCE seq_workspace_events START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspace_events (
    id            NUMBER(19)   NOT NULL,
    workspace_id  NUMBER(19)   NOT NULL,
    type          VARCHAR2(30) NOT NULL,
    actor_name    VARCHAR2(100) NULL,
    pr_id         NUMBER(19)   NULL,
    issue_id      NUMBER(19)   NULL,
    channel_id    NUMBER(19)   NULL,
    repository_id NUMBER(19)   NULL,
    thread_id     NUMBER(19)   NULL,
    content       CLOB         NULL,
    created_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_workspace_events PRIMARY KEY (id),
    CONSTRAINT fk_workspace_events_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT chk_workspace_events_type CHECK (type IN ('PR_CREATED', 'ISSUE_CREATED', 'PR_REVIEW', 'MENTION', 'REPLY'))
);

CREATE OR REPLACE TRIGGER trg_workspace_events_bi
  BEFORE INSERT ON workspace_events
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_workspace_events.NEXTVAL;
  END IF;
END;
/
