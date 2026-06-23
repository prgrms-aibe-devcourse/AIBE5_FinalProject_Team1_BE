-- PR 승인 시 GithubPullRequest.state = 'approved'로 저장하는데,
-- V4의 chk_github_prs_state CHECK 제약이 ('open','merged','closed')만 허용해
-- ORA-02290으로 승인이 롤백됨. 'approved'를 허용 목록에 추가한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_constraints
    WHERE constraint_name = 'CHK_GITHUB_PRS_STATE'
      AND table_name = 'GITHUB_PULL_REQUESTS';

    IF v_count > 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE github_pull_requests DROP CONSTRAINT chk_github_prs_state';
    END IF;

    EXECUTE IMMEDIATE 'ALTER TABLE github_pull_requests ADD CONSTRAINT chk_github_prs_state ' ||
                      'CHECK (state IN (''open'', ''merged'', ''closed'', ''approved''))';
END;
/
