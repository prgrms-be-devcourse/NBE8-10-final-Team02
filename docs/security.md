---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-quality-ops-v7
applies_to: security-policy
---

# Security

이 문서는 MVP 기준 보안 구현 원본이다.
기능 범위는 `docs/requirements.md`, 오류 응답은 `docs/error-policy.md`, API 인증 계약은 `openapi.yaml`, 구현 규칙은 `docs/backend-conventions.md`를 함께 따른다.

## 1. 적용 범위

- OAuth2 로그인과 토큰 기반 인증
- 사용자 리소스 인가와 소유권 검증
- GitHub 연동과 private repository 접근 제어
- 문서 업로드와 추출 파이프라인 보안
- AI 생성 입력 데이터 최소화
- 운영 로그, secret, 보안 이벤트 관리

## 2. 인증 정책

### 2.1 로그인 제공자

- 로그인 제공자는 GitHub, Google, Kakao만 허용한다.
- 지원하지 않는 provider 값은 허용하지 않는다.
- 로그인 계정 연결 정보와 GitHub 동기화 연결 정보는 분리해서 관리한다.
  - `auth_accounts`: 로그인 수단
  - `github_connections`: GitHub 데이터 동기화 수단

### 2.2 인증 수단 우선순위

- 보호 API는 인증된 사용자만 접근 가능해야 한다.
- 보호 API의 기본 경로 범위는 `/api/**`로 두고, 현재 canonical base URL은 `/api/v1`을 사용한다.
- 런타임에서 `/api/v1` base path가 전역 적용되지 않았다면 `/users/**`, `/github/**`, `/documents/**`, `/applications/**`, `/interview/**`, `/auth/logout`도 함께 보호해야 한다.
- OAuth2 인가 시작/콜백, actuator, swagger 문서는 보호 범위 예외로 둘 수 있다.
- 인증 정보는 아래 우선순위를 따른다.
  1. `Authorization` 헤더
  2. 헤더가 없을 때만 쿠키 `apiKey`, `accessToken`
- 헤더 형식은 `Bearer {apiKey} {accessToken}`를 사용한다.
- 인증 실패, 토큰 만료, 위조 가능성이 있는 토큰은 모두 정상 사용자로 처리하면 안 된다.

### 2.3 토큰 처리 기준

- access token, refresh token, OAuth code 원문은 로그에 남기지 않는다.
- refresh 재발급 실패 시에는 조용히 무시하지 않고 재로그인을 유도한다.
- 로그아웃 시 서버가 보유한 세션 또는 토큰 추적 정보가 있다면 함께 무효화한다.

## 3. 인가와 소유권 검증

### 3.1 사용자 리소스 기본 원칙

- 사용자는 자신의 `document`, `application`, `question set`, `interview session`, `result`만 조회/수정/삭제 가능해야 한다.
- 요청 본문이나 query로 전달된 `userId`를 신뢰하지 않는다.
- path id로 리소스를 찾더라도 소유권 검증을 생략하지 않는다.

### 3.2 상태 전이 보호

- 이미 종료된 세션에는 일반 답변을 다시 저장할 수 없다.
- 결과 생성이 끝나지 않은 세션을 `feedback_completed`로 직접 바꾸면 안 된다.
- 참조 중인 문서나 repository를 삭제할 때는 현재 연결 상태를 검증해야 한다.

### 3.3 private repository 접근 제어

- GitHub URL 입력만으로는 public repository만 조회한다.
- private repository는 GitHub OAuth 추가 동의와 적절한 scope가 있을 때만 허용한다.
- 로그인 제공자가 GitHub가 아니어도 별도 GitHub 연결을 통해 private repository 접근을 허용할 수 있다.
- scope가 부족하면 repository가 존재하더라도 접근 성공으로 처리하지 않는다.

## 4. 입력/업로드 보안

### 4.1 파일 업로드 검증

- 허용 형식은 PDF, DOCX, MD만 허용한다.
- 확장자와 MIME type을 함께 검증한다.
- 파일당 최대 용량은 10MB, 사용자당 기본 업로드 수는 5개다.
- 저장 경로에 원본 파일명을 직접 사용하지 않는다.
- 동일 문서 재업로드는 파일 해시 기반 중복 감지를 우선 검토한다.

### 4.2 문서 추출 보안

- 업로드 성공과 텍스트 추출 성공은 분리해 기록한다.
- 암호화 문서, 손상 문서, 스캔 PDF는 실패 상태로 저장할 수 있어야 한다.
- 문서 원문 전체와 추출 텍스트 전체를 운영 로그에 남기지 않는다.

## 5. AI 입력 데이터 보호

- 생성형 AI에 전달하는 데이터는 목적에 필요한 최소 범위만 포함한다.
- 다른 사용자 데이터가 같은 프롬프트 컨텍스트에 섞이면 안 된다.
- 사용자 문서 원문 전체를 그대로 전달하기보다 필요한 발췌와 정규화된 요약을 우선 사용한다.
- AI 응답이 형식 불일치 또는 빈 응답인 경우 정상 결과로 저장하지 않는다.

## 6. Secret 및 저장 보호

- 환경 변수, secret, API key는 저장소에 커밋하지 않는다.
- GitHub access token 등 외부 연동 credential을 저장해야 하는 경우 암호화 저장을 기본값으로 한다.
- 운영 스토리지와 DB 접근 권한은 최소 권한 원칙을 따른다.
- 로컬 개발 환경과 운영 환경의 secret을 같은 값으로 재사용하지 않는다.

## 7. 속도 제한과 남용 방지

- 로그인, 토큰 재발급, GitHub 동기화, AI 생성 API는 rate limit 또는 동등한 보호 장치를 둘 수 있어야 한다.
- 활성 면접 세션은 사용자당 1개만 허용한다.
- 동일 요청의 무한 재시도는 허용하지 않는다.
- 실패가 반복되는 외부 연동은 경고 로그와 메트릭으로 식별 가능해야 한다.

## 8. 보안 로그와 이벤트

### 8.1 필수 추적 항목

- requestId 또는 traceId
- userId 또는 anonymous 여부
- provider 또는 외부 연동 대상
- 오류 코드
- 상태코드
- 소요 시간

### 8.2 기록해야 하는 주요 이벤트

- OAuth 로그인 성공/실패
- GitHub 연결 생성/해제
- private repository scope 부족 또는 접근 거부
- 문서 업로드 차단과 추출 실패
- 비정상 반복 요청 또는 rate limit 발생

## 9. 최소 수용 기준

아래 항목을 모두 만족할 때 현재 보안 기준을 충족한 것으로 본다.

- 인증 없는 보호 API 요청은 성공하면 안 된다.
- 다른 사용자 리소스 접근은 소유권 검증을 통과하면 안 된다.
- private repository는 추가 동의와 scope 없이는 조회/동기화되면 안 된다.
- 지원하지 않는 형식 또는 10MB 초과 파일은 업로드되면 안 된다.
- access token, refresh token, OAuth code, 문서 원문 전체, 면접 답변 원문 전체는 운영 로그에 남지 않아야 한다.
- AI 프롬프트 컨텍스트에 다른 사용자 데이터가 섞이지 않아야 한다.
