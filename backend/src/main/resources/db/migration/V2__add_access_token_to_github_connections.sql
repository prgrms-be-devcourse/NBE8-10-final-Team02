-- github_connections 테이블에 OAuth access token 컬럼 추가.
-- GitHub OAuth 로그인 사용자의 token을 저장해 API 호출(private repo, rate limit 5000)에 사용한다.
-- 운영 환경에서는 암호화 저장을 권장한다 (backend-conventions.md §12.3).
alter table github_connections
    add column access_token text;