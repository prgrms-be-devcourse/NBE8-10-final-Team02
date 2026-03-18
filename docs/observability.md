---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-quality-ops-v7
applies_to: logs-metrics-health
---

# Observability

이 문서는 MVP 운영을 위한 로그, 메트릭, 헬스체크 기준 원본이다.
장애 대응 절차는 `docs/deployment-runbook.md`와 `docs/incidents.md`, 오류 분류는 `docs/error-policy.md`를 함께 따른다.

## 1. 목적

- 장애를 빠르게 탐지하고 원인을 좁힌다.
- 외부 연동 실패와 내부 버그를 구분해서 본다.
- 사용자 문의가 들어왔을 때 requestId 기준으로 흐름을 재구성할 수 있게 한다.
- AI 생성 품질 저하, 문서 추출 실패율 증가, GitHub 연동 문제를 조기에 본다.

## 2. 요청 추적 기준

### 2.1 공통 추적 키

- 모든 요청은 `requestId` 또는 `traceId`를 가진다.
- 응답 `meta.requestId`, 서버 로그, 외부 연동 로그가 같은 추적 키로 연결되어야 한다.
- 비동기 작업이 생기면 부모 요청 ID와 작업 ID를 함께 남긴다.

### 2.2 사용자 식별 원칙

- 가능하면 `userId`를 기록하되, 인증 전 요청은 `anonymous`로 구분한다.
- 이메일, access token, 문서 원문 전체를 추적 키로 사용하지 않는다.

## 3. 구조화 로그 기준

### 3.1 필수 필드

- timestamp
- level
- requestId 또는 traceId
- method
- path
- statusCode
- errorCode
- durationMs
- userId 또는 anonymous
- externalTarget - github, oauth, ai, storage 등
- retryCount

### 3.2 주요 로그 이벤트

- OAuth 로그인 성공/실패
- GitHub 연결 성공/실패
- repository 조회 및 commit 동기화 시작/종료
- 문서 업로드 성공/차단/추출 실패
- 자소서 생성 시작/성공/실패
- 면접 질문 생성 시작/성공/실패
- 세션 시작, pause, resume, complete
- 결과 생성 성공/실패

### 3.3 로그 금지 항목

- access token
- refresh token
- OAuth code 전체 값
- 문서 원문 전체
- 면접 답변 원문 전체
- AI 프롬프트 전문 전체
- 외부 API secret

## 4. 메트릭 기준

### 4.1 기본 메트릭

- 요청 수, 오류 수, 지연 시간 p50/p95/p99
- HTTP 상태코드별 카운트
- 외부 연동 대상별 성공/실패/timeout 수
- 큐 또는 비동기 작업이 생기면 적체 수와 실패 수

### 4.2 도메인별 핵심 메트릭

인증
- 로그인 성공 수
- 로그인 실패 수
- 토큰 재발급 실패 수

GitHub 연동
- repository 조회 성공/실패 수
- commit 동기화 성공/실패 수
- rate limit 발생 수
- scope 부족 발생 수

문서 처리
- 업로드 성공/차단 수
- 텍스트 추출 성공/실패 수
- 추출 평균/상위 지연 시간

AI 생성
- 템플릿별 생성 성공/실패 수
- timeout 수
- malformed response 수
- schema violation 수

면접 세션
- 시작/완료/자동 pause/resume 수
- 활성 세션 수
- 결과 생성 성공/실패 수

## 5. 헬스체크 기준

- 애플리케이션 health endpoint가 필요하다.
- 최소한 `liveness`와 `readiness`를 분리한다.
- readiness는 DB 연결, 필수 외부 의존성, 스토리지 접근 가능성 중 현재 운영에 필요한 항목을 확인한다.
- 외부 AI 제공자 장애가 있을 때 전체 readiness를 바로 실패로 둘지, 일부 기능 저하로 볼지는 운영 정책에 따라 분리한다.

## 6. 알림 초안

MVP 기준으로 아래 상황은 우선 알림 후보로 둔다.

- 5xx 비율 급증
- OAuth 로그인 실패율 급증
- GitHub 연동 실패율 또는 rate limit 급증
- 문서 추출 실패율 급증
- 자소서/질문/결과 생성 실패율 급증
- p95 응답 시간 임계치 초과 지속
- 활성 세션 저장 실패 또는 결과 저장 실패 발생

## 7. 운영 분석 원칙

- 장애 분석은 requestId 기준으로 시작한다.
- 외부 연동 장애인지 내부 비즈니스 오류인지 먼저 분리한다.
- 같은 오류 코드가 반복되면 사용자 행동 문제인지 시스템 문제인지 메트릭과 로그를 함께 본다.
- AI 품질 문제는 성공률만 보지 말고 질문 수 불일치, 점수 누락, schema violation도 함께 본다.

## 8. 보관과 개인정보 보호

- 로그 보관 기간은 운영 정책으로 별도 정하되, 개인정보 최소화를 전제로 한다.
- 디버그 로그를 운영에 장기간 켜 두지 않는다.
- 추적을 위해 필요한 경우에도 사용자 원문 전체 저장보다 요약/마스킹을 우선한다.

## 9. 현재 수용 기준

아래 항목을 만족하면 현재 관측성 기준을 충족한 것으로 본다.

- 모든 API 응답에서 requestId 추적이 가능하다.
- 오류 로그에서 errorCode와 외부 연동 대상을 식별할 수 있다.
- GitHub, 문서 추출, AI 생성, 면접 세션의 성공/실패 메트릭이 분리된다.
- health endpoint로 애플리케이션과 핵심 의존성 상태를 구분할 수 있다.
- 민감정보가 로그에 그대로 남지 않는다.
