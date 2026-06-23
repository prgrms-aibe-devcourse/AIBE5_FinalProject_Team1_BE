ALTER TABLE workspace_events ADD (
    target_user_id NUMBER(19)    NULL,
    is_read        NUMBER(1,0)   DEFAULT 0 NOT NULL
);

ALTER TABLE workspace_events ADD CONSTRAINT chk_workspace_events_is_read CHECK (is_read IN (0, 1));
