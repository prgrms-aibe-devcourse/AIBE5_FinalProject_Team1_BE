-- ============================================================
-- V4: PR / Issue
-- github_pull_requests, pull_request_files, pull_request_labels,
-- pull_request_review_requests, pull_request_analysis,
-- pull_request_diff_lines, pull_request_checklist_items,
-- pull_request_analysis_findings,
-- github_issues, issue_assignees, issue_labels
-- ============================================================


-- ------------------------------------------------------------
-- github_pull_requests
-- labels: JSONB -> CLOB (GitHub 원본 스냅샷)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_github_prs START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE github_pull_requests (
    id                  NUMBER(19)    NOT NULL,
    github_pr_id        VARCHAR2(100) NOT NULL,
    repository_id       NUMBER(19)    NOT NULL,
    channel_id          NUMBER(19)    NOT NULL,
    pr_number           NUMBER(10)    NOT NULL,
    title               VARCHAR2(255) NOT NULL,
    description         CLOB          NULL,
    state               VARCHAR2(50)  NOT NULL,
    url                 CLOB          NOT NULL,
    author              VARCHAR2(100) NULL,
    head_branch         VARCHAR2(255) NULL,
    base_branch         VARCHAR2(255) NULL,
    labels              CLOB          NULL,
    additions           NUMBER(10)    NOT NULL,
    deletions           NUMBER(10)    NOT NULL,
    changed_files_count NUMBER(10)    NOT NULL,
    merged_at           TIMESTAMP     NULL,
    github_created_at   TIMESTAMP     NULL,
    github_updated_at   TIMESTAMP     NULL,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    CONSTRAINT pk_github_prs PRIMARY KEY (id),
    CONSTRAINT uq_github_prs UNIQUE (repository_id, github_pr_id),
    CONSTRAINT fk_github_prs_repo FOREIGN KEY (repository_id) REFERENCES github_repositories (id),
    CONSTRAINT fk_github_prs_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT chk_github_prs_state CHECK (state IN ('open', 'merged', 'closed'))
);

CREATE OR REPLACE TRIGGER trg_github_prs_bi
  BEFORE INSERT ON github_pull_requests
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_github_prs.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_files
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_files START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_files (
    id                     NUMBER(19)    NOT NULL,
    github_pull_request_id NUMBER(19)    NOT NULL,
    filename               VARCHAR2(255) NOT NULL,
    status                 VARCHAR2(50)  NULL,
    additions              NUMBER(10)    NOT NULL,
    deletions              NUMBER(10)    NOT NULL,
    path                   CLOB          NULL,
    patch                  CLOB          NULL,
    created_at             TIMESTAMP     NOT NULL,
    CONSTRAINT pk_pr_files PRIMARY KEY (id),
    CONSTRAINT fk_pr_files_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id)
);

CREATE OR REPLACE TRIGGER trg_pr_files_bi
  BEFORE INSERT ON pull_request_files
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_files.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_labels
-- 검색/필터용 / JSONB는 GitHub 원본 스냅샷용
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_labels START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_labels (
    id                     NUMBER(19)    NOT NULL,
    github_pull_request_id NUMBER(19)    NOT NULL,
    name                   VARCHAR2(100) NOT NULL,
    color                  VARCHAR2(7)   NOT NULL,
    created_at             TIMESTAMP     NOT NULL,
    CONSTRAINT pk_pr_labels PRIMARY KEY (id),
    CONSTRAINT fk_pr_labels_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id)
);

CREATE OR REPLACE TRIGGER trg_pr_labels_bi
  BEFORE INSERT ON pull_request_labels
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_labels.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_review_requests
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_review_requests START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_review_requests (
    id                     NUMBER(19)  NOT NULL,
    github_pull_request_id NUMBER(19)  NOT NULL,
    workspace_member_id    NUMBER(19)  NOT NULL,
    created_at             TIMESTAMP   NOT NULL,
    CONSTRAINT pk_pr_review_requests PRIMARY KEY (id),
    CONSTRAINT uq_pr_review_requests UNIQUE (github_pull_request_id, workspace_member_id),
    CONSTRAINT fk_pr_review_req_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT fk_pr_review_req_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_pr_review_requests_bi
  BEFORE INSERT ON pull_request_review_requests
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_review_requests.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_analysis
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_analysis START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_analysis (
    id                     NUMBER(19)   NOT NULL,
    github_pull_request_id NUMBER(19)   NOT NULL,
    risk_level             VARCHAR2(10) NULL,
    tests_passed           NUMBER(10)   NULL,
    review_room_active     NUMBER(1,0)  DEFAULT 0 NOT NULL,
    created_at             TIMESTAMP    NOT NULL,
    updated_at             TIMESTAMP    NOT NULL,
    CONSTRAINT pk_pr_analysis PRIMARY KEY (id),
    CONSTRAINT uq_pr_analysis UNIQUE (github_pull_request_id),
    CONSTRAINT fk_pr_analysis_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT chk_pr_analysis_risk CHECK (risk_level IS NULL OR risk_level IN ('Low', 'Medium', 'High')),
    CONSTRAINT chk_pr_analysis_active CHECK (review_room_active IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_pr_analysis_bi
  BEFORE INSERT ON pull_request_analysis
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_analysis.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_diff_lines
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_diff_lines START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_diff_lines (
    id                     NUMBER(19)   NOT NULL,
    github_pull_request_id NUMBER(19)   NOT NULL,
    pull_request_file_id   NUMBER(19)   NOT NULL,
    line_type              VARCHAR2(20) NOT NULL,
    old_line_number        NUMBER(10)   NULL,
    new_line_number        NUMBER(10)   NULL,
    content                CLOB         NOT NULL,
    created_at             TIMESTAMP    NOT NULL,
    CONSTRAINT pk_pr_diff_lines PRIMARY KEY (id),
    CONSTRAINT fk_pr_diff_lines_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT fk_pr_diff_lines_file FOREIGN KEY (pull_request_file_id) REFERENCES pull_request_files (id),
    CONSTRAINT chk_pr_diff_lines_type CHECK (line_type IN ('added', 'removed', 'context'))
);

CREATE OR REPLACE TRIGGER trg_pr_diff_lines_bi
  BEFORE INSERT ON pull_request_diff_lines
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_diff_lines.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_checklist_items
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_checklist START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_checklist_items (
    id                     NUMBER(19)  NOT NULL,
    github_pull_request_id NUMBER(19)  NOT NULL,
    content                CLOB        NOT NULL,
    is_checked             NUMBER(1,0) DEFAULT 0 NOT NULL,
    created_by_id          NUMBER(19)  NULL,
    created_at             TIMESTAMP   NOT NULL,
    updated_at             TIMESTAMP   NOT NULL,
    CONSTRAINT pk_pr_checklist PRIMARY KEY (id),
    CONSTRAINT fk_pr_checklist_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT fk_pr_checklist_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id),
    CONSTRAINT chk_pr_checklist_checked CHECK (is_checked IN (0, 1))
);

CREATE OR REPLACE TRIGGER trg_pr_checklist_bi
  BEFORE INSERT ON pull_request_checklist_items
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_checklist.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_analysis_findings
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_findings START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_analysis_findings (
    id                       NUMBER(19)   NOT NULL,
    pull_request_analysis_id NUMBER(19)   NOT NULL,
    pull_request_file_id     NUMBER(19)   NULL,
    finding_type             VARCHAR2(50) NOT NULL,
    severity                 VARCHAR2(20) NOT NULL,
    line_number              NUMBER(10)   NULL,
    description              CLOB         NOT NULL,
    suggestion               CLOB         NULL,
    created_at               TIMESTAMP    NOT NULL,
    CONSTRAINT pk_pr_findings PRIMARY KEY (id),
    CONSTRAINT fk_pr_findings_analysis FOREIGN KEY (pull_request_analysis_id) REFERENCES pull_request_analysis (id),
    CONSTRAINT fk_pr_findings_file FOREIGN KEY (pull_request_file_id) REFERENCES pull_request_files (id),
    CONSTRAINT chk_pr_findings_type CHECK (finding_type IN ('security', 'performance', 'style')),
    CONSTRAINT chk_pr_findings_severity CHECK (severity IN ('high', 'medium', 'low'))
);

CREATE OR REPLACE TRIGGER trg_pr_findings_bi
  BEFORE INSERT ON pull_request_analysis_findings
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_findings.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- github_issues
-- state:        GitHub 원본 상태 — open / closed
-- local_status: 서비스 내부 작업 보드 상태
-- labels: JSONB -> CLOB (GitHub 원본 스냅샷)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_github_issues START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE github_issues (
    id                NUMBER(19)    NOT NULL,
    github_issue_id   VARCHAR2(100) NOT NULL,
    repository_id     NUMBER(19)    NOT NULL,
    channel_id        NUMBER(19)    NOT NULL,
    issue_number      NUMBER(10)    NOT NULL,
    title             VARCHAR2(255) NOT NULL,
    description       CLOB          NULL,
    state             VARCHAR2(50)  NOT NULL,
    local_status      VARCHAR2(50)  NULL,
    url               CLOB          NOT NULL,
    author            VARCHAR2(100) NULL,
    priority          VARCHAR2(20)  NULL,
    issue_type        VARCHAR2(50)  NULL,
    labels            CLOB          NULL,
    closed_at         TIMESTAMP     NULL,
    github_created_at TIMESTAMP     NULL,
    github_updated_at TIMESTAMP     NULL,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    CONSTRAINT pk_github_issues PRIMARY KEY (id),
    CONSTRAINT uq_github_issues UNIQUE (repository_id, github_issue_id),
    CONSTRAINT fk_github_issues_repo FOREIGN KEY (repository_id) REFERENCES github_repositories (id),
    CONSTRAINT fk_github_issues_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT chk_github_issues_state CHECK (state IN ('open', 'closed')),
    CONSTRAINT chk_github_issues_status CHECK (local_status IS NULL OR local_status IN ('todo', 'in_progress', 'review', 'done', 'blocked')),
    CONSTRAINT chk_github_issues_priority CHECK (priority IS NULL OR priority IN ('high', 'medium', 'low'))
);

CREATE OR REPLACE TRIGGER trg_github_issues_bi
  BEFORE INSERT ON github_issues
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_github_issues.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- issue_assignees
-- ------------------------------------------------------------
CREATE SEQUENCE seq_issue_assignees START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE issue_assignees (
    id                  NUMBER(19) NOT NULL,
    github_issue_id     NUMBER(19) NOT NULL,
    workspace_member_id NUMBER(19) NOT NULL,
    created_at          TIMESTAMP  NOT NULL,
    CONSTRAINT pk_issue_assignees PRIMARY KEY (id),
    CONSTRAINT uq_issue_assignees UNIQUE (github_issue_id, workspace_member_id),
    CONSTRAINT fk_issue_assignees_issue FOREIGN KEY (github_issue_id) REFERENCES github_issues (id),
    CONSTRAINT fk_issue_assignees_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_issue_assignees_bi
  BEFORE INSERT ON issue_assignees
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_issue_assignees.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- issue_labels
-- 검색/필터용 / JSONB는 GitHub 원본 스냅샷용
-- ------------------------------------------------------------
CREATE SEQUENCE seq_issue_labels START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE issue_labels (
    id              NUMBER(19)    NOT NULL,
    github_issue_id NUMBER(19)    NOT NULL,
    name            VARCHAR2(100) NOT NULL,
    color           VARCHAR2(7)   NOT NULL,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT pk_issue_labels PRIMARY KEY (id),
    CONSTRAINT fk_issue_labels_issue FOREIGN KEY (github_issue_id) REFERENCES github_issues (id)
);

CREATE OR REPLACE TRIGGER trg_issue_labels_bi
  BEFORE INSERT ON issue_labels
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_issue_labels.NEXTVAL;
  END IF;
END;
/
