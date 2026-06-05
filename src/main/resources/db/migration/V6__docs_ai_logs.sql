-- ============================================================
-- V6: 문서 / AI / 로그
-- documents, api_specs, erd_tables, erd_documents,
-- ai_summaries, activity_logs
-- ============================================================


-- ------------------------------------------------------------
-- documents
-- ------------------------------------------------------------
CREATE SEQUENCE seq_documents START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE documents (
    id            NUMBER(19)    NOT NULL,
    workspace_id  NUMBER(19)    NOT NULL,
    created_by_id NUMBER(19)    NOT NULL,
    title         VARCHAR2(255) NOT NULL,
    content       CLOB          NULL,
    category      VARCHAR2(50)  NULL,
    generated_by  VARCHAR2(20)  NULL,
    related_pr_id NUMBER(19)    NULL,
    visibility    VARCHAR2(30)  DEFAULT 'workspace' NOT NULL,
    deleted_at    TIMESTAMP     NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT fk_documents_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_documents_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_documents_pr FOREIGN KEY (related_pr_id) REFERENCES github_pull_requests (id),
    CONSTRAINT chk_documents_category CHECK (category IS NULL OR category IN ('pr-summary', 'manual', 'meeting', 'release')),
    CONSTRAINT chk_documents_generated_by CHECK (generated_by IS NULL OR generated_by IN ('AI', 'Template', 'Manual')),
    CONSTRAINT chk_documents_visibility CHECK (visibility IN ('workspace', 'private', 'public'))
);

CREATE OR REPLACE TRIGGER trg_documents_bi
  BEFORE INSERT ON documents
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_documents.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- api_specs
-- ------------------------------------------------------------
CREATE SEQUENCE seq_api_specs START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE api_specs (
    id               NUMBER(19)    NOT NULL,
    workspace_id     NUMBER(19)    NOT NULL,
    created_by_id    NUMBER(19)    NOT NULL,
    title            VARCHAR2(255) NOT NULL,
    method           VARCHAR2(10)  NOT NULL,
    endpoint         VARCHAR2(255) NOT NULL,
    group_name       VARCHAR2(100) NULL,
    entity           VARCHAR2(100) NULL,
    summary          VARCHAR2(255) NULL,
    description      CLOB          NULL,
    status           VARCHAR2(30)  NULL,
    assignee_id      NUMBER(19)    NULL,
    path_params      CLOB          NULL,
    headers          CLOB          NULL,
    query_params     CLOB          NULL,
    request_body     CLOB          NULL,
    response_status  NUMBER(10)    NULL,
    response_body    CLOB          NULL,
    version          VARCHAR2(50)  NULL,
    source_type      VARCHAR2(30)  NULL,
    related_issue_id NUMBER(19)    NULL,
    related_pr_id    NUMBER(19)    NULL,
    note             CLOB          NULL,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    CONSTRAINT pk_api_specs PRIMARY KEY (id),
    CONSTRAINT fk_api_specs_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_api_specs_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_api_specs_assignee FOREIGN KEY (assignee_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_api_specs_issue FOREIGN KEY (related_issue_id) REFERENCES github_issues (id),
    CONSTRAINT fk_api_specs_pr FOREIGN KEY (related_pr_id) REFERENCES github_pull_requests (id),
    CONSTRAINT chk_api_specs_method CHECK (method IN ('GET', 'POST', 'PUT', 'PATCH', 'DELETE')),
    CONSTRAINT chk_api_specs_status CHECK (status IS NULL OR status IN ('completed', 'in_progress', 'design')),
    CONSTRAINT chk_api_specs_source CHECK (source_type IS NULL OR source_type IN ('manual', 'github', 'imported'))
);

CREATE OR REPLACE TRIGGER trg_api_specs_bi
  BEFORE INSERT ON api_specs
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_api_specs.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- erd_tables
-- schema_definition: JSONB -> CLOB
-- ------------------------------------------------------------
CREATE SEQUENCE seq_erd_tables START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE erd_tables (
    id                NUMBER(19)    NOT NULL,
    workspace_id      NUMBER(19)    NOT NULL,
    created_by_id     NUMBER(19)    NOT NULL,
    table_name        VARCHAR2(150) NOT NULL,
    schema_definition CLOB          NULL,
    description       CLOB          NULL,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    CONSTRAINT pk_erd_tables PRIMARY KEY (id),
    CONSTRAINT fk_erd_tables_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_erd_tables_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_erd_tables_bi
  BEFORE INSERT ON erd_tables
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_erd_tables.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- erd_documents
-- ------------------------------------------------------------
CREATE SEQUENCE seq_erd_documents START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE erd_documents (
    id            NUMBER(19)    NOT NULL,
    workspace_id  NUMBER(19)    NOT NULL,
    created_by_id NUMBER(19)    NOT NULL,
    title         VARCHAR2(255) NOT NULL,
    description   CLOB          NULL,
    mermaid_code  CLOB          NULL,
    deleted_at    TIMESTAMP     NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_erd_documents PRIMARY KEY (id),
    CONSTRAINT fk_erd_documents_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_erd_documents_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_erd_documents_bi
  BEFORE INSERT ON erd_documents
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_erd_documents.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- ai_summaries
-- summary: pending/processing 상태에서는 NULL
-- ------------------------------------------------------------
CREATE SEQUENCE seq_ai_summaries START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE ai_summaries (
    id                     NUMBER(19)    NOT NULL,
    github_issue_id        NUMBER(19)    NULL,
    github_pull_request_id NUMBER(19)    NULL,
    summary                CLOB          NULL,
    risk_level             VARCHAR2(20)  NULL,
    status                 VARCHAR2(30)  DEFAULT 'pending' NOT NULL,
    model_version          VARCHAR2(100) NULL,
    created_at             TIMESTAMP     NOT NULL,
    updated_at             TIMESTAMP     NOT NULL,
    CONSTRAINT pk_ai_summaries PRIMARY KEY (id),
    CONSTRAINT fk_ai_summaries_issue FOREIGN KEY (github_issue_id) REFERENCES github_issues (id),
    CONSTRAINT fk_ai_summaries_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT chk_ai_summaries_target CHECK (
        (github_issue_id IS NOT NULL AND github_pull_request_id IS NULL) OR
        (github_issue_id IS NULL AND github_pull_request_id IS NOT NULL)
    ),
    CONSTRAINT chk_ai_summaries_risk CHECK (risk_level IS NULL OR risk_level IN ('Low', 'Medium', 'High')),
    CONSTRAINT chk_ai_summaries_status CHECK (status IN ('pending', 'processing', 'completed', 'failed'))
);

CREATE OR REPLACE TRIGGER trg_ai_summaries_bi
  BEFORE INSERT ON ai_summaries
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_ai_summaries.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- activity_logs
-- changes: JSONB -> CLOB
-- ------------------------------------------------------------
CREATE SEQUENCE seq_activity_logs START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE activity_logs (
    id                  NUMBER(19)    NOT NULL,
    workspace_id        NUMBER(19)    NOT NULL,
    workspace_member_id NUMBER(19)    NULL,
    entity_type         VARCHAR2(50)  NOT NULL,
    entity_id           NUMBER(19)    NOT NULL,
    action              VARCHAR2(100) NOT NULL,
    changes             CLOB          NULL,
    created_at          TIMESTAMP     NOT NULL,
    CONSTRAINT pk_activity_logs PRIMARY KEY (id),
    CONSTRAINT fk_activity_logs_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_activity_logs_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_activity_logs_bi
  BEFORE INSERT ON activity_logs
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_activity_logs.NEXTVAL;
  END IF;
END;
/
