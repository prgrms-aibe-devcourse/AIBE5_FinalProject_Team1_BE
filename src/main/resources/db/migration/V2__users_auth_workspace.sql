-- ============================================================
-- V2: 사용자 / 인증 / 워크스페이스
-- users, user_skills, refresh_tokens, password_reset_tokens,
-- workspaces, workspace_members, workspace_member_preferences,
-- invitations
-- ============================================================


-- ------------------------------------------------------------
-- users
-- ------------------------------------------------------------
CREATE SEQUENCE seq_users START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE users (
    id                      NUMBER(19)    NOT NULL,
    email                   VARCHAR2(255) NOT NULL,
    password_hash           VARCHAR2(255) NULL,
    email_verified          NUMBER(1,0)   DEFAULT 0 NOT NULL,
    email_verified_at       TIMESTAMP     NULL,
    username                VARCHAR2(100) NOT NULL,
    display_name            VARCHAR2(100) NULL,
    nickname                VARCHAR2(50)  NULL,
    developer_type          VARCHAR2(100) NULL,
    bio                     VARCHAR2(160) NULL,
    avatar_url              CLOB          NULL,
    github_id               VARCHAR2(100) NULL,
    github_username         VARCHAR2(100) NULL,
    github_email            VARCHAR2(255) NULL,
    github_connected        NUMBER(1,0)   DEFAULT 0 NOT NULL,
    github_connected_at     TIMESTAMP     NULL,
    github_access_token     CLOB          NULL,
    github_token_expires_at TIMESTAMP     NULL,
    is_active               NUMBER(1,0)   DEFAULT 1 NOT NULL,
    deactivated_at          TIMESTAMP     NULL,
    last_login_at           TIMESTAMP     NULL,
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_github_id UNIQUE (github_id),
    CONSTRAINT chk_users_email_verified CHECK (email_verified IN (0, 1)),
    CONSTRAINT chk_users_github_connected CHECK (github_connected IN (0, 1)),
    CONSTRAINT chk_users_is_active CHECK (is_active IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_users_bi
  BEFORE INSERT ON users
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_users.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- user_skills
-- ------------------------------------------------------------
CREATE SEQUENCE seq_user_skills START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE user_skills (
    id         NUMBER(19)    NOT NULL,
    user_id    NUMBER(19)    NOT NULL,
    skill_name VARCHAR2(100) NOT NULL,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT pk_user_skills PRIMARY KEY (id),
    CONSTRAINT uq_user_skills UNIQUE (user_id, skill_name),
    CONSTRAINT fk_user_skills_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE OR REPLACE TRIGGER trg_user_skills_bi
  BEFORE INSERT ON user_skills
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_user_skills.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- refresh_tokens
-- token: TEXT -> VARCHAR2(500) (CLOB은 UNIQUE 불가 / JWT는 500자 이내)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_refresh_tokens START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE refresh_tokens (
    id         NUMBER(19)    NOT NULL,
    user_id    NUMBER(19)    NOT NULL,
    token      VARCHAR2(500) NOT NULL,
    expires_at TIMESTAMP     NOT NULL,
    revoked    NUMBER(1,0)   DEFAULT 0 NOT NULL,
    revoked_at TIMESTAMP     NULL,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token UNIQUE (token),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_refresh_tokens_revoked CHECK (revoked IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_refresh_tokens_bi
  BEFORE INSERT ON refresh_tokens
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_refresh_tokens.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- password_reset_tokens
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pwd_reset_tokens START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE password_reset_tokens (
    id         NUMBER(19)    NOT NULL,
    user_id    NUMBER(19)    NOT NULL,
    token      VARCHAR2(255) NOT NULL,
    expires_at TIMESTAMP     NOT NULL,
    used       NUMBER(1,0)   DEFAULT 0 NOT NULL,
    used_at    TIMESTAMP     NULL,
    created_at TIMESTAMP     NOT NULL,
    CONSTRAINT pk_pwd_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uq_pwd_reset_tokens_token UNIQUE (token),
    CONSTRAINT fk_pwd_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_pwd_reset_tokens_used CHECK (used IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_pwd_reset_tokens_bi
  BEFORE INSERT ON password_reset_tokens
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pwd_reset_tokens.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- workspaces
-- created_by_id: 최초 생성자 (변경 불가)
-- owner_id:      현재 소유자 (소유권 이전 시 변경)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_workspaces START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspaces (
    id            NUMBER(19)    NOT NULL,
    created_by_id NUMBER(19)    NOT NULL,
    owner_id      NUMBER(19)    NOT NULL,
    name          VARCHAR2(100) NOT NULL,
    slug          VARCHAR2(120) NOT NULL,
    description   CLOB          NULL,
    logo_url      CLOB          NULL,
    created_at    TIMESTAMP     NOT NULL,
    updated_at    TIMESTAMP     NOT NULL,
    CONSTRAINT pk_workspaces PRIMARY KEY (id),
    CONSTRAINT uq_workspaces_slug UNIQUE (slug),
    CONSTRAINT fk_ws_created_by FOREIGN KEY (created_by_id) REFERENCES users (id),
    CONSTRAINT fk_ws_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE OR REPLACE TRIGGER trg_workspaces_bi
  BEFORE INSERT ON workspaces
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_workspaces.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- workspace_members
-- authority: owner / admin / editor / viewer
-- ------------------------------------------------------------
CREATE SEQUENCE seq_workspace_members START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspace_members (
    id           NUMBER(19)    NOT NULL,
    workspace_id NUMBER(19)    NOT NULL,
    user_id      NUMBER(19)    NOT NULL,
    authority    VARCHAR2(30)  NOT NULL,
    position     VARCHAR2(100) NULL,
    is_active    NUMBER(1,0)   DEFAULT 1 NOT NULL,
    left_at      TIMESTAMP     NULL,
    left_reason  VARCHAR2(255) NULL,
    created_at   TIMESTAMP     NOT NULL,
    updated_at   TIMESTAMP     NOT NULL,
    CONSTRAINT pk_workspace_members PRIMARY KEY (id),
    CONSTRAINT uq_workspace_members UNIQUE (workspace_id, user_id),
    CONSTRAINT fk_wm_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_wm_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT chk_wm_authority CHECK (authority IN ('owner', 'admin', 'editor', 'viewer')),
    CONSTRAINT chk_wm_is_active CHECK (is_active IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_workspace_members_bi
  BEFORE INSERT ON workspace_members
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_workspace_members.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- workspace_member_preferences
-- presence: DB = 마지막 저장 상태 / Redis = 실시간 상태
-- ------------------------------------------------------------
CREATE SEQUENCE seq_wmp START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE workspace_member_preferences (
    id                  NUMBER(19)   NOT NULL,
    workspace_member_id NUMBER(19)   NOT NULL,
    notification_mode   VARCHAR2(20) DEFAULT 'mentions' NOT NULL,
    presence            VARCHAR2(20) DEFAULT 'active'   NOT NULL,
    updated_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_wmp PRIMARY KEY (id),
    CONSTRAINT uq_wmp_member UNIQUE (workspace_member_id),
    CONSTRAINT fk_wmp_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT chk_wmp_notification CHECK (notification_mode IN ('all', 'mentions', 'muted')),
    CONSTRAINT chk_wmp_presence CHECK (presence IN ('active', 'away', 'busy', 'offline'))
);

CREATE OR REPLACE TRIGGER trg_wmp_bi
  BEFORE INSERT ON workspace_member_preferences
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_wmp.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- invitations
-- invited_authority: 초대받는 권한 (owner 제외)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_invitations START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE invitations (
    id                NUMBER(19)    NOT NULL,
    workspace_id      NUMBER(19)    NOT NULL,
    inviter_member_id NUMBER(19)    NOT NULL,
    invited_email     VARCHAR2(255) NOT NULL,
    invited_authority VARCHAR2(30)  NOT NULL,
    token             VARCHAR2(255) NOT NULL,
    status            VARCHAR2(20)  DEFAULT 'pending' NOT NULL,
    revoked_at        TIMESTAMP     NULL,
    revoked_by_id     NUMBER(19)    NULL,
    expires_at        TIMESTAMP     NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    CONSTRAINT pk_invitations PRIMARY KEY (id),
    CONSTRAINT uq_invitations_token UNIQUE (token),
    CONSTRAINT fk_inv_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_inv_inviter FOREIGN KEY (inviter_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_inv_revoked_by FOREIGN KEY (revoked_by_id) REFERENCES workspace_members (id),
    CONSTRAINT chk_inv_authority CHECK (invited_authority IN ('admin', 'editor', 'viewer')),
    CONSTRAINT chk_inv_status CHECK (status IN ('pending', 'accepted', 'rejected', 'expired', 'revoked'))
);

CREATE OR REPLACE TRIGGER trg_invitations_bi
  BEFORE INSERT ON invitations
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_invitations.NEXTVAL;
  END IF;
END;
/
