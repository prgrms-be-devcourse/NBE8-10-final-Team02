---
owner: 팀 전체
reviewer: 팀 전체
status: draft
last_updated: 2026-03-17
linked_issue_or_pr: -
applies_to: system-architecture
---

# Architecture

## 1. 목적

이 문서는 AI와 팀원이 빠르게 공통 구조를 이해하도록 서비스 아키텍처를 요약한다.

## 2. 핵심 도메인

- Auth / User
- GitHub Portfolio
- Document Upload / Extraction
- Application
- AI Generation
- Interview Session / Result

## 3. 핵심 설계 결정

### 3.1 로그인 계정과 GitHub 연동을 분리한다

- 로그인은 `auth_accounts`
- 포트폴리오 동기화는 `github_connections`
- 따라서 Google/Kakao 로그인 사용자도 GitHub를 별도로 연동할 수 있다.

### 3.2 `application`을 생성 흐름의 중심으로 둔다

- 하나의 `application`이 회사명, 직무, 자소서 문항, 선택 소스, 면접 질문 생성 기준을 묶는다.
- 자소서 생성과 면접 질문 생성의 입력 축을 하나로 통일하기 위한 구조다.

### 3.3 질문 세트와 실제 세션을 분리한다

- `interview_question_sets`: 생성된 질문 템플릿
- `interview_sessions`: 실제 사용자 연습 세션
- 같은 질문 세트를 여러 세션에서 재사용할 수 있다.

## 4. 상위 흐름

1. OAuth2 로그인
2. GitHub 연동 및 repository 선택
3. 문서 업로드 및 텍스트 추출
4. 포트폴리오 데이터 저장
5. `application` 생성
6. 자소서 문항 생성 및 저장
7. 면접 질문 세트 생성
8. 모의 면접 세션 진행
9. 결과 저장 및 히스토리 조회

## 5. 시스템 경계

### 내부 시스템

- 사용자/인증
- 포트폴리오 저장
- application 관리
- 면접 세션 및 결과 저장

### 외부 시스템

- OAuth2 Provider: GitHub, Google, Kakao
- GitHub API
- 문서 추출 엔진
- AI 모델 API

## 6. 데이터 저장 원칙

- 핵심 관계는 PostgreSQL 기준으로 설계
- 상태값은 숨기지 않고 컬럼으로 저장
  - `sync_status`
  - `extract_status`
  - `application.status`
  - `interview_session.status`
- 추출 실패나 동기화 실패도 이력으로 남긴다.

## 7. AI 관점에서 중요한 해석 규칙

- 입력 데이터가 부족하면 품질 저하 또는 실패를 명시해야 한다.
- 없는 프로젝트, 없는 성과 수치, 없는 회사 정보를 만들면 안 된다.
- 생성 결과는 JSON 구조와 저장 스키마를 만족해야 한다.

## 8. 구현 순서 권장

1. 공통 응답/예외/인증 구조
2. GitHub / Document 수집
3. application 도메인
4. AI 생성 파이프라인
5. interview 세션/결과
6. 테스트/운영 보강
