-- ============================================================
-- Load Test Seed Data (load-test 프로파일 전용)
-- 운영 DB에는 절대 적용되지 않는다 (application-load-test.yml에서만 이 경로를 스캔).
--
-- 고정 ID 체계 (999xxx 대역 — 실제 데이터와 충돌 방지):
--   users.id              = 999999
--   github_connections.id = 999999
--   github_repositories.id = 999001 (express, 경량)
--                           999002 (flask,   중량)
--                           999003 (spring-boot, 대형)
-- ============================================================

-- ── 1. 테스트 유저 ─────────────────────────────────────────────────
INSERT INTO users (id, email, display_name, status)
OVERRIDING SYSTEM VALUE
VALUES (999999, 'load-test@loadtest.internal', '부하테스트유저', 'active')
ON CONFLICT (id) DO NOTHING;

-- ── 2. Auth Account ────────────────────────────────────────────────
INSERT INTO auth_accounts (user_id, provider, provider_user_id, provider_email, is_primary, connected_at)
VALUES (999999, 'github', 'load-test-ghost-999999', 'load-test@loadtest.internal', true, now())
ON CONFLICT (provider, provider_user_id) DO NOTHING;

-- ── 3. GitHub Connection (id 고정) ────────────────────────────────
-- access_token null: 공개 레포는 인증 없이 clone 가능
INSERT INTO github_connections (id, user_id, github_user_id, github_login, access_token, sync_status, connected_at)
OVERRIDING SYSTEM VALUE
VALUES (999999, 999999, 999999, 'load-test-ghost', null, 'success', now())
ON CONFLICT (id) DO NOTHING;

-- ── 4. GitHub Repositories (id 고정) ──────────────────────────────

-- 경량 (~5MB): expressjs/express
INSERT INTO github_repositories (
    id, github_connection_id, github_repo_id,
    owner_login, repo_name, full_name, html_url,
    visibility, default_branch, is_selected, synced_at
)
OVERRIDING SYSTEM VALUE
VALUES (
    999001, 999999, 10001,
    'expressjs', 'express', 'expressjs/express', 'https://github.com/expressjs/express',
    'public', 'master', true, now()
)
ON CONFLICT (id) DO NOTHING;

-- 중량 (~30MB): pallets/flask
INSERT INTO github_repositories (
    id, github_connection_id, github_repo_id,
    owner_login, repo_name, full_name, html_url,
    visibility, default_branch, is_selected, synced_at
)
OVERRIDING SYSTEM VALUE
VALUES (
    999002, 999999, 10002,
    'pallets', 'flask', 'pallets/flask', 'https://github.com/pallets/flask',
    'public', 'main', true, now()
)
ON CONFLICT (id) DO NOTHING;

-- 대형 (~150MB): spring-projects/spring-boot
INSERT INTO github_repositories (
    id, github_connection_id, github_repo_id,
    owner_login, repo_name, full_name, html_url,
    visibility, default_branch, is_selected, synced_at
)
OVERRIDING SYSTEM VALUE
VALUES (
    999003, 999999, 10003,
    'spring-projects', 'spring-boot', 'spring-projects/spring-boot', 'https://github.com/spring-projects/spring-boot',
    'public', 'main', true, now()
)
ON CONFLICT (id) DO NOTHING;
