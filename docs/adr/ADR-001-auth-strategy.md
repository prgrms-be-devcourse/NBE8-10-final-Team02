---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: draft
last_updated: 2026-03-17
linked_issue_or_pr: -
applies_to: adr
---

# ADR-001: Separate auth accounts from GitHub connections

- 상태: Accepted
- 날짜: 2026-03-17

## 배경

사용자는 GitHub, Google, Kakao 중 하나로 로그인할 수 있다.
동시에 서비스의 핵심 데이터는 GitHub repository / commit 기반 포트폴리오를 사용한다.

## 결정

로그인 계정 연결과 GitHub 포트폴리오 연동을 분리한다.

- 로그인: `auth_accounts`
- GitHub 포트폴리오 연동: `github_connections`

## 이유

- Google/Kakao 로그인 사용자도 GitHub를 연결할 수 있어야 한다.
- 인증 로직과 포트폴리오 동기화 로직의 책임을 분리할 수 있다.
- 향후 GitHub 연동 재설정, scope 변경, sync 상태 관리가 더 명확해진다.

## 결과

- API와 DB 모두 두 구조를 따르도록 유지한다.
- AI가 인증 기능을 구현할 때 GitHub sync 책임을 섞지 않도록 한다.
