-- ============================================================
-- V5: 채팅 / 협업
-- threads, thread_replies,
-- pull_request_reviews, pull_request_review_comments,
-- thread_attachments, reactions, bookmarks,
-- channel_read_status, mentions
-- ============================================================


-- ------------------------------------------------------------
-- threads
-- reply_to_id: 채널 내 인용 답장 (자기 참조)
-- ------------------------------------------------------------
CREATE SEQUENCE seq_threads START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE threads (
    id              NUMBER(19)    NOT NULL,
    channel_id      NUMBER(19)    NOT NULL,
    created_by_id   NUMBER(19)    NULL,
    reply_to_id     NUMBER(19)    NULL,
    thread_type     VARCHAR2(50)  NOT NULL,
    threadable_type VARCHAR2(50)  NULL,
    threadable_id   NUMBER(19)    NULL,
    title           VARCHAR2(255) NULL,
    content         CLOB          NULL,
    created_at      TIMESTAMP     NOT NULL,
    updated_at      TIMESTAMP     NOT NULL,
    CONSTRAINT pk_threads PRIMARY KEY (id),
    CONSTRAINT fk_threads_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT fk_threads_creator FOREIGN KEY (created_by_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_threads_reply_to FOREIGN KEY (reply_to_id) REFERENCES threads (id),
    CONSTRAINT chk_threads_type CHECK (thread_type IN ('user_message', 'github_bot_notification', 'system')),
    CONSTRAINT chk_threads_threadable CHECK (threadable_type IS NULL OR threadable_type IN ('github_issue', 'github_pull_request'))
);

CREATE OR REPLACE TRIGGER trg_threads_bi
  BEFORE INSERT ON threads
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_threads.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- thread_replies
-- ------------------------------------------------------------
CREATE SEQUENCE seq_thread_replies START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE thread_replies (
    id                  NUMBER(19) NOT NULL,
    thread_id           NUMBER(19) NOT NULL,
    workspace_member_id NUMBER(19) NOT NULL,
    content             CLOB       NOT NULL,
    created_at          TIMESTAMP  NOT NULL,
    updated_at          TIMESTAMP  NOT NULL,
    CONSTRAINT pk_thread_replies PRIMARY KEY (id),
    CONSTRAINT fk_thread_replies_thread FOREIGN KEY (thread_id) REFERENCES threads (id),
    CONSTRAINT fk_thread_replies_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id)
);

CREATE OR REPLACE TRIGGER trg_thread_replies_bi
  BEFORE INSERT ON thread_replies
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_thread_replies.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_reviews
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_reviews START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_reviews (
    id                     NUMBER(19)    NOT NULL,
    github_pull_request_id NUMBER(19)    NOT NULL,
    workspace_member_id    NUMBER(19)    NOT NULL,
    github_review_id       VARCHAR2(100) NULL,
    review_state           VARCHAR2(50)  NOT NULL,
    thread_reply_id        NUMBER(19)    NULL,
    created_at             TIMESTAMP     NOT NULL,
    updated_at             TIMESTAMP     NOT NULL,
    CONSTRAINT pk_pr_reviews PRIMARY KEY (id),
    CONSTRAINT fk_pr_reviews_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT fk_pr_reviews_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_pr_reviews_reply FOREIGN KEY (thread_reply_id) REFERENCES thread_replies (id),
    CONSTRAINT chk_pr_reviews_state CHECK (review_state IN ('approved', 'changes_requested', 'commented'))
);

CREATE OR REPLACE TRIGGER trg_pr_reviews_bi
  BEFORE INSERT ON pull_request_reviews
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_reviews.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- pull_request_review_comments
-- ------------------------------------------------------------
CREATE SEQUENCE seq_pr_review_comments START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE pull_request_review_comments (
    id                     NUMBER(19) NOT NULL,
    github_pull_request_id NUMBER(19) NOT NULL,
    diff_line_id           NUMBER(19) NULL,
    workspace_member_id    NUMBER(19) NOT NULL,
    thread_reply_id        NUMBER(19) NULL,
    content                CLOB       NOT NULL,
    created_at             TIMESTAMP  NOT NULL,
    updated_at             TIMESTAMP  NOT NULL,
    CONSTRAINT pk_pr_review_comments PRIMARY KEY (id),
    CONSTRAINT fk_pr_rev_comments_pr FOREIGN KEY (github_pull_request_id) REFERENCES github_pull_requests (id),
    CONSTRAINT fk_pr_rev_comments_line FOREIGN KEY (diff_line_id) REFERENCES pull_request_diff_lines (id),
    CONSTRAINT fk_pr_rev_comments_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_pr_rev_comments_reply FOREIGN KEY (thread_reply_id) REFERENCES thread_replies (id)
);

CREATE OR REPLACE TRIGGER trg_pr_review_comments_bi
  BEFORE INSERT ON pull_request_review_comments
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_pr_review_comments.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- thread_attachments
-- ------------------------------------------------------------
CREATE SEQUENCE seq_thread_attachments START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE thread_attachments (
    id              NUMBER(19)    NOT NULL,
    thread_id       NUMBER(19)    NOT NULL,
    attachment_type VARCHAR2(30)  NOT NULL,
    target_id       NUMBER(19)    NULL,
    url             CLOB          NULL,
    title           VARCHAR2(255) NULL,
    detail          VARCHAR2(255) NULL,
    meta            VARCHAR2(100) NULL,
    preview_url     CLOB          NULL,
    mime_type       VARCHAR2(100) NULL,
    file_size       NUMBER(19)    NULL,
    created_at      TIMESTAMP     NOT NULL,
    CONSTRAINT pk_thread_attachments PRIMARY KEY (id),
    CONSTRAINT fk_thread_attachments FOREIGN KEY (thread_id) REFERENCES threads (id),
    CONSTRAINT chk_thread_attach_type CHECK (attachment_type IN ('file', 'image', 'link', 'pr', 'issue', 'api', 'erd', 'docs'))
);

CREATE OR REPLACE TRIGGER trg_thread_attachments_bi
  BEFORE INSERT ON thread_attachments
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_thread_attachments.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- reactions
-- ------------------------------------------------------------
CREATE SEQUENCE seq_reactions START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE reactions (
    id                  NUMBER(19)   NOT NULL,
    workspace_member_id NUMBER(19)   NOT NULL,
    target_type         VARCHAR2(30) NOT NULL,
    target_id           NUMBER(19)   NOT NULL,
    emoji               VARCHAR2(50) NOT NULL,
    created_at          TIMESTAMP    NOT NULL,
    CONSTRAINT pk_reactions PRIMARY KEY (id),
    CONSTRAINT uq_reactions UNIQUE (workspace_member_id, target_type, target_id, emoji),
    CONSTRAINT fk_reactions_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT chk_reactions_target CHECK (target_type IN ('thread', 'thread_reply'))
);

CREATE OR REPLACE TRIGGER trg_reactions_bi
  BEFORE INSERT ON reactions
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_reactions.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- bookmarks
-- ------------------------------------------------------------
CREATE SEQUENCE seq_bookmarks START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE bookmarks (
    id                  NUMBER(19) NOT NULL,
    workspace_member_id NUMBER(19) NOT NULL,
    thread_id           NUMBER(19) NOT NULL,
    created_at          TIMESTAMP  NOT NULL,
    CONSTRAINT pk_bookmarks PRIMARY KEY (id),
    CONSTRAINT uq_bookmarks UNIQUE (workspace_member_id, thread_id),
    CONSTRAINT fk_bookmarks_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_bookmarks_thread FOREIGN KEY (thread_id) REFERENCES threads (id)
);

CREATE OR REPLACE TRIGGER trg_bookmarks_bi
  BEFORE INSERT ON bookmarks
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_bookmarks.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- channel_read_status
-- ------------------------------------------------------------
CREATE SEQUENCE seq_channel_read_status START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE channel_read_status (
    id                  NUMBER(19) NOT NULL,
    channel_id          NUMBER(19) NOT NULL,
    workspace_member_id NUMBER(19) NOT NULL,
    last_read_thread_id NUMBER(19) NULL,
    last_read_at        TIMESTAMP  NOT NULL,
    CONSTRAINT pk_channel_read_status PRIMARY KEY (id),
    CONSTRAINT uq_channel_read_status UNIQUE (channel_id, workspace_member_id),
    CONSTRAINT fk_crs_channel FOREIGN KEY (channel_id) REFERENCES channels (id),
    CONSTRAINT fk_crs_member FOREIGN KEY (workspace_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_crs_last_thread FOREIGN KEY (last_read_thread_id) REFERENCES threads (id)
);

CREATE OR REPLACE TRIGGER trg_channel_read_status_bi
  BEFORE INSERT ON channel_read_status
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_channel_read_status.NEXTVAL;
  END IF;
END;
/


-- ------------------------------------------------------------
-- mentions
-- ------------------------------------------------------------
CREATE SEQUENCE seq_mentions START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE mentions (
    id                     NUMBER(19)  NOT NULL,
    workspace_id           NUMBER(19)  NOT NULL,
    thread_id              NUMBER(19)  NULL,
    thread_reply_id        NUMBER(19)  NULL,
    mentioned_member_id    NUMBER(19)  NOT NULL,
    mentioned_by_member_id NUMBER(19)  NOT NULL,
    is_read                NUMBER(1,0) DEFAULT 0 NOT NULL,
    created_at             TIMESTAMP   NOT NULL,
    CONSTRAINT pk_mentions PRIMARY KEY (id),
    CONSTRAINT fk_mentions_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_mentions_thread FOREIGN KEY (thread_id) REFERENCES threads (id),
    CONSTRAINT fk_mentions_reply FOREIGN KEY (thread_reply_id) REFERENCES thread_replies (id),
    CONSTRAINT fk_mentions_mentioned FOREIGN KEY (mentioned_member_id) REFERENCES workspace_members (id),
    CONSTRAINT fk_mentions_by_member FOREIGN KEY (mentioned_by_member_id) REFERENCES workspace_members (id),
    CONSTRAINT chk_mentions_is_read CHECK (is_read IN (0, 1)),
    CONSTRAINT chk_mentions_target CHECK (thread_id IS NOT NULL OR thread_reply_id IS NOT NULL)
);

CREATE OR REPLACE TRIGGER trg_mentions_bi
  BEFORE INSERT ON mentions
  FOR EACH ROW
BEGIN
  IF :NEW.id IS NULL THEN
    :NEW.id := seq_mentions.NEXTVAL;
  END IF;
END;
/
