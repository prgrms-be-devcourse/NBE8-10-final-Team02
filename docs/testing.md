---
owner: 포트폴리오 수집/외부 연동 + 품질/테스트
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-quality-ops-v7
applies_to: testing-and-quality
---

# Testing Strategy

이 문서는 MVP 구현에 필요한 최소 테스트 기준 원본이다.
기능 범위는 `docs/requirements.md`, API 계약은 `openapi.yaml`, 오류 처리 기준은 `docs/error-policy.md`, 구현 규칙은 `docs/backend-conventions.md`를 함께 따른다.

## 1. 테스트 목적

- 기능 요구사항과 API 계약이 실제 동작과 어긋나지 않도록 검증한다.
- 소유권, 상태 전이, 외부 연동 실패처럼 버그가 나기 쉬운 경계를 우선 보호한다.
- 문서 변경과 코드 변경이 함께 일어나도록 테스트를 동기화한다.

## 2. 테스트 계층

### 2.1 Unit Test

대상
- 순수 비즈니스 로직
- mapper, formatter, validator
- 점수 계산, 상태 전이, 분량 옵션 분기

원칙
- 외부 연동 없이 빠르게 실행되어야 한다.
- 정상 흐름과 실패 흐름을 함께 검증한다.
- 날짜/시간 의존 로직은 고정 Clock 또는 테스트 더블을 사용한다.

### 2.2 Integration Test

대상
- JPA 매핑
- repository 쿼리
- unique 제약과 FK 제약
- transaction 경계와 상태 저장 일관성

원칙
- PostgreSQL 기준 동작을 검증한다.
- 가능하면 Testcontainers를 사용해 실제 DB 제약을 확인한다.
- 삭제 제한, 중복 저장 방지, 상태 전이 충돌을 우선 검증한다.

### 2.3 API Test

대상
- 인증/인가
- request validation
- 공통 응답 형식
- 상태코드, 오류 코드, pagination

원칙
- `openapi.yaml`과 응답 구조가 어긋나지 않아야 한다.
- 정상 응답뿐 아니라 `401`, `404/403`, `409`, `422`, `5xx` 흐름을 함께 검증한다.
- 활성 세션 1개 제한, 업로드 제약, 문항 수/질문 수 제한 같은 숫자 규칙을 빠뜨리지 않는다.

### 2.4 External Adapter Test

대상
- GitHub API client
- OAuth provider client
- AI API client
- 파일 스토리지/추출 어댑터

원칙
- CI에서 실제 외부 서비스를 호출하지 않는다.
- HTTP 스텁 또는 테스트 더블로 성공, timeout, malformed response, rate limit을 검증한다.
- 외부 응답 스키마를 도메인 모델로 변환하는 매핑 실패를 명시적으로 테스트한다.

## 3. 도메인별 필수 시나리오

### 3.1 인증과 회원

- OAuth 로그인 성공/실패
- 지원하지 않는 provider 차단
- 토큰 만료 후 재발급 성공/실패
- 같은 이메일의 다른 소셜 로그인 자동 병합 금지
- 탈퇴 사용자 또는 비활성 사용자 접근 제한

### 3.2 포트폴리오와 GitHub 연동

- public repository 목록 조회
- private repository scope 부족 처리
- commit 동기화 성공/부분 실패/실패 분리
- 중복 repository 저장 방지
- 최근 2년 활동 repository 우선 정렬
- GitHub API rate limit 및 권한 오류 분기

### 3.3 문서 업로드와 추출

- PDF, DOCX, MD 업로드 성공
- 지원하지 않는 형식 차단
- 10MB 초과 차단
- 업로드 개수 5개 제한
- 추출 성공과 추출 실패 상태 분리
- 동일 문서 재업로드 시 덮어쓰기/취소 분기
- 스캔 PDF 또는 비정상 추출 결과 품질 경고

### 3.4 자소서 생성

- application 생성/수정/삭제
- source repository, source document 연결 저장
- 직무 필수값 검증
- 문항 최대 10개 제한
- `lengthOption`이 `short`, `medium`, `long`만 허용되는지 검증
- AI timeout, 빈 응답, schema mismatch 처리
- 재생성 시 이전 결과 이력 보존

### 3.5 면접 질문 생성

- 질문 세트 생성 성공
- 질문 최대 20개 제한
- 질문 유형/난이도 저장
- 자소서 미생성 상태 차단
- 질문 개별 삭제/수동 추가

### 3.6 모의 면접과 결과

- 활성 세션 1개 제한
- 일반 답변 50~1000자 검증
- `isSkipped=true` 예외 흐름
- 30분 무응답 자동 pause
- 새로고침/브라우저 이탈 후 resume
- 세션 완료 후 결과 생성
- 결과 생성 실패 후 재시도
- 점수, 태그, 종합 코멘트 저장
- 완료 세션 재답변 차단

## 4. 핵심 품질 위험과 우선순위

가장 먼저 보호해야 하는 버그 유형은 아래와 같다.

1. 다른 사용자 데이터 접근 가능
2. 중복 저장 또는 상태 꼬임
3. 외부 연동 실패를 정상 성공처럼 저장
4. AI 응답 형식 불일치를 정상 결과로 저장
5. 문서/세션/결과의 관계 무결성 붕괴

PR 리뷰와 CI는 이 다섯 가지를 우선적으로 방어해야 한다.

## 5. 테스트 데이터와 환경 기준

- DB 통합 테스트는 PostgreSQL 기준으로 검증한다.
- Redis 사용 기능이 생기면 임베디드 대체보다 실제 동작에 가까운 테스트 환경을 우선한다.
- 파일 업로드 테스트는 실제 파일 바이트와 MIME type을 함께 검증한다.
- 개인정보가 포함된 실제 이력서나 면접 답변 원문을 테스트 fixture로 커밋하지 않는다.
- AI 응답 fixture는 정상 응답, 빈 응답, malformed JSON, schema violation을 모두 포함한다.

## 6. CI 최소 게이트

MVP 기준으로 merge 전 최소한 아래를 통과해야 한다.

- 변경된 도메인의 unit test
- 관련 integration test
- 관련 API test
- 새 오류 코드 또는 상태값 추가 시 대응 테스트
- `openapi.yaml`과 구현 차이가 생긴 경우 계약 수정 또는 테스트 수정

테스트를 임시로 끄거나 `@Disabled` 처리했다면 PR 본문에 이유와 복구 계획을 남겨야 한다.

## 7. 문서와 테스트 동기화 규칙

- API path, 요청/응답 구조가 바뀌면 `openapi.yaml`과 API 테스트를 함께 수정한다.
- 오류 코드가 바뀌면 `docs/error-policy.md`, `docs/error-codes.md`, 오류 응답 테스트를 함께 수정한다.
- DB 제약이 바뀌면 `docs/db/schema.md`와 integration test를 함께 수정한다.
- 숫자 제한이 바뀌면 요구사항 문서와 validation 테스트를 함께 수정한다.
- 세션 상태 enum이 바뀌면 API, DB, 화면, 테스트를 함께 수정한다.

## 8. 현재 수용 기준

아래 항목을 만족하면 현재 테스트 기준을 충족한 것으로 본다.

- FR-01부터 FR-06까지 최소 한 개 이상의 정상 흐름 테스트가 있다.
- 각 FR마다 최소 한 개 이상의 실패 흐름 테스트가 있다.
- 인증/소유권/상태 전이/중복 방지 테스트가 빠지지 않는다.
- 외부 연동 timeout과 형식 불일치 테스트가 있다.
- CI가 문서 기준과 어긋난 구현을 조기에 잡을 수 있다.
