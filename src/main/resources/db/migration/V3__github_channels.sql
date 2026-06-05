-- ============================================================
-- V3: GitHub 연동 기반
-- github_repositories, channels
-- ============================================================


-- ------------------------------------------------------------
-- github_repositories
-- webhook_events: TEXT[] -> CLOB (JSON 배열로 저장)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_github_repos START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE github_repositories (
    id                       NUMBER(19)    NOT NULL,
    workspace_id             NUMBER(19)    NOT NULL,
    github_repo_id           VARCHAR2(100) NOT NULL,
    owner                    VARCHAR2(100) NOT NULL,
    name                     VARCHAR2(150) NOT NULL,
    full_name                VARCHAR2(255) NOT NULL,
    url                      CLOB          NOT NULL,
    description              CLOB          NULL,
    is_private               NUMBER(1,0)   DEFAULT 1 NOT NULL,
    default_branch           VARCHAR2(255) NULL,
    last_synced_at           TIMESTAMP     NULL,
    webhook_id               VARCHAR2(100) NULL,
    webhook_secret           VARCHAR2(255) NULL,
    webhook_url              CLOB          NULL,
    webhook_events           CLOB          NULL,
    webhook_active           NUMBER(1,0)   DEFAULT 0 NOT NULL,
    webhook_last_delivery_at TIMESTAMP     NULL,
    webhook_last_status      VARCHAR2(50)  NULL,
    created_at               TIMESTAMP     NOT NULL,
    updated_at               TIMESTAMP     NOT NULL,
    CONSTRAINT pk_github_repos PRIMARY KEY (id),
    CONSTRAINT uq_github_repos UNIQUE (workspace_id, github_repo_id),
    CONSTRAINT fk_github_repos_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT chk_github_repos_private CHECK (is_private IN (0, 1)),
    CONSTRAINT chk_github_repos_webhook CHECK (webhook_active IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_github_repos_bi
  BEFORE INSERT ON github_repositories
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_github_repos.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- channels
-- ------------------------------------------------------------
CREATE SEQUENCE seq_channels START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE channels (
    id                   NUMBER(19)    NOT NULL,
    workspace_id         NUMBER(19)    NOT NULL,
    github_repository_id NUMBER(19)    NULL,
    name                 VARCHAR2(120) NOT NULL,
    channel_type         VARCHAR2(30)  NOT NULL,
    is_deletable         NUMBER(1,0)   NOT NULL,
    description          CLOB          NULL,
    created_at           TIMESTAMP     NOT NULL,
    updated_at           TIMESTAMP     NOT NULL,
    CONSTRAINT pk_channels PRIMARY KEY (id),
    CONSTRAINT uq_channels UNIQUE (workspace_id, name),
    CONSTRAINT fk_channels_ws FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_channels_repo FOREIGN KEY (github_repository_id) REFERENCES github_repositories (id),
    CONSTRAINT chk_channels_type CHECK (channel_type IN ('general', 'repository', 'custom')),
    CONSTRAINT chk_channels_is_deletable CHECK (is_deletable IN (0, 1)),
    CONSTRAINT chk_channels_repo_type CHECK (
        (channel_type = 'repository' AND github_repository_id IS NOT NULL) OR
        (channel_type != 'repository')
    )
);

CREATE OR REPLACE TRIGGER trg_channels_bi
  BEFORE INSERT ON channels
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_channels.NEXTVAL;
  END IF;
END;
/
