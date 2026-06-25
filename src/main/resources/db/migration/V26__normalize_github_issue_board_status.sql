-- 작업보드는 github_issues.local_status 기준으로 컬럼을 나눔.
-- 이미 적재된 closed/todo 이슈는 첫 조회 전에 DB에서 보정해야 함.
-- GitHub state와 작업보드 상태가 어긋난 기존 데이터를 한 번 정리함.
-- 이후 생성/동기화 이슈는 GithubIssue 엔티티에서 같은 규칙으로 보정함.

UPDATE github_issues
SET local_status = 'done'
WHERE LOWER(state) = 'closed'
  AND (local_status IS NULL OR LOWER(TRIM(local_status)) <> 'done');

UPDATE github_issues
SET local_status = 'todo'
WHERE LOWER(state) <> 'closed'
  AND (local_status IS NULL OR TRIM(local_status) IS NULL);
