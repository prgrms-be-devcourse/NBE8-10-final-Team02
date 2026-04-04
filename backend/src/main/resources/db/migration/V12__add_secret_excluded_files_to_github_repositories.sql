-- 시크릿 스캔 결과로 분석에서 제외된 파일 목록을 저장한다.
-- 구조: [{"filePath": "...", "ruleId": "..."}, ...]
-- null: 스캔 미실행 또는 발견 없음
ALTER TABLE github_repositories
    ADD COLUMN IF NOT EXISTS secret_excluded_files jsonb;
