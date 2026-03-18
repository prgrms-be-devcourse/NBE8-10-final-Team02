---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-requirements-v5
applies_to: auth-domain
---

# Domain: Auth

## 목적
사용자 식별, OAuth2 로그인, 세션/토큰 기반 접근 제어를 담당한다.

## 핵심 엔티티
- `users`
- `auth_accounts`

## 핵심 규칙
- 하나의 사용자는 여러 OAuth 계정을 연결할 수 있다.
- `(provider, provider_user_id)`는 유일해야 한다.
- 신규 로그인 시 회원 생성 후 즉시 진입 가능해야 한다.
- GitHub 로그인 여부와 GitHub 포트폴리오 연동 여부는 별개다.
- 같은 이메일의 다른 소셜 로그인은 자동 병합하지 않는다.
- 회원 탈퇴는 즉시 완전 삭제가 아니라 30일 유예 후 최종 삭제로 처리한다.

## 주요 API
- `GET /auth/oauth2/{provider}/authorize`
- `GET /auth/oauth2/{provider}/callback`
- `GET /users/me`
- `POST /auth/logout`

## 대표 오류
- `AUTH_REQUIRED`
- `AUTH_UNSUPPORTED_PROVIDER`
- `AUTH_OAUTH_CANCELLED`
- `AUTH_PROVIDER_RESPONSE_INVALID`
- `AUTH_ACCOUNT_MERGE_REQUIRED`
- `USER_WITHDRAWN`
