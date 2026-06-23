-- GithubPullRequest 엔티티의 commitsJson 필드에 대응하는 컬럼이 V4 테이블 생성 시 누락되어
-- github_pull_requests 조회 시 ORA-00904 (COMMITS_JSON 부적합한 식별자)가 발생, PR 동기화가 전부 실패함.
-- 누락된 commits_json 컬럼을 추가한다.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*)
    INTO v_count
    FROM user_tab_columns
    WHERE table_name = 'GITHUB_PULL_REQUESTS'
      AND column_name = 'COMMITS_JSON';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE github_pull_requests ADD (commits_json CLOB NULL)';
    END IF;
END;
/
