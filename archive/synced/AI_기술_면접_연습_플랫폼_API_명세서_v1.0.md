# AI 기술 면접 연습 플랫폼 API 명세서

> 이 문서는 `archive/synced/` 보관용 동기화 사본입니다.
> HTTP 계약의 최종 원본은 `openapi.yaml` 이고, 운영 규칙 원본은 `docs/api-guidelines.md` 입니다.
> 오류 정책은 `docs/error-policy.md`, 기능 범위는 `docs/requirements.md`, 저장 구조는 `docs/db/schema.md` 를 함께 봅니다.

## 1. 문서 개요

- 문서명: AI 기술 면접 연습 플랫폼 API 명세서
- 버전: v1.0
- 동기화 기준일: 2026-03-17
- 보관 목적: `archive/originals/`의 초기 API 초안을 현재 canonical 문서 기준으로 다시 정리한 참조용 사본
- 최종 원본
  - HTTP 계약: `openapi.yaml`
  - 운영 규칙: `docs/api-guidelines.md`
  - 오류 정책: `docs/error-policy.md`

## 2. 설계 기준 요약

- API는 현재 path를 canonical 기준으로 사용한다.
- 인증은 OAuth2 로그인 이후 발급된 토큰 또는 세션 기준으로 처리한다.
- 성공 응답은 `success`, `data`, `meta` 구조를 사용한다.
- 실패 응답은 `docs/error-policy.md`의 `ErrorResponse` 구조와 오류 코드 정책을 따른다.
- 엔티티를 응답으로 직접 노출하지 않고, API DTO/계약 모델 기준으로 응답한다.
- 삭제, 부분 실패, 외부 연동 실패는 사용자 메시지와 내부 로그를 분리해서 다룬다.

## 3. 공통 규칙

### 3.1 Content-Type
- JSON 요청/응답: `application/json`
- 문서 업로드: `multipart/form-data`

### 3.2 인증 규칙
- OAuth2 로그인 시작: `GET /auth/oauth2/{provider}/authorize`
- OAuth2 콜백: `GET /auth/oauth2/{provider}/callback`
- 인증 필요 API는 로그인 이후 사용자 소유 리소스에 한해 접근한다.

### 3.3 공통 응답 형식
- 성공: `success: true`, `data`, `meta`
- 실패: `success: false`, `error.code`, `error.message`, 필요 시 `fieldErrors`

### 3.4 페이징/정렬/필터
- 목록 API는 필요한 경우 `page`, `size`, 필터 query parameter를 사용한다.
- 현재 `GET /github/repositories`는 `selected`, `visibility`, `page`, `size`를 지원한다.

### 3.5 날짜/시간 형식
- 시간 값은 ISO-8601 문자열을 기준으로 처리한다.
- 추적/운영 시에는 `requestId`, `traceId` 기반으로 요청을 식별한다.

## 4. 공통 Enum 동기화본

| enum | 값 |
|---|---|
| `user_status` | `active`, `withdrawn` |
| `auth_provider` | `github`, `google`, `kakao` |
| `github_sync_status` | `pending`, `success`, `failed` |
| `repository_visibility` | `public`, `private`, `internal` |
| `document_type` | `resume`, `award`, `certificate`, `other` |
| `extract_status` | `pending`, `success`, `failed` |
| `application_status` | `draft`, `ready` |
| `tone_option` | `formal`, `balanced`, `casual` |
| `length_option` | `short`, `medium`, `long` |
| `difficulty_level` | `easy`, `medium`, `hard` |
| `interview_question_type` | `experience`, `project`, `technical_cs`, `technical_stack`, `behavioral`, `follow_up` |
| `interview_session_status` | `ready`, `in_progress`, `paused`, `completed`, `feedback_completed` |
| `feedback_tag_category` | `content`, `structure`, `evidence`, `communication`, `technical`, `other` |

## 5. 주요 API 그룹

## 인증/회원 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `GET` | `/auth/oauth2/{provider}/authorize` | OAuth2 로그인 시작 | provider(path) | 200, 400 |
| `GET` | `/auth/oauth2/{provider}/callback` | OAuth2 콜백 처리 | provider(path), code(query), state(query), error(query) | 200, 401 |
| `GET` | `/users/me` | 내 정보 조회 | - | 200, 401 |
| `POST` | `/auth/logout` | 로그아웃 | - | 200 |
## GitHub 포트폴리오 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/github/connections` | GitHub 연결 생성 또는 갱신 | application/json | 201 |
| `GET` | `/github/repositories` | 저장소 목록 조회 | selected(query), visibility(query), page(query), size(query) | 200 |
| `PUT` | `/github/repositories/selection` | 저장소 선택 상태 저장 | application/json | 200 |
| `POST` | `/github/repositories/{repositoryId}/sync-commits` | 저장소 커밋 동기화 시작 | repositoryId(path) | 202, 403 |
## 문서 업로드 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/documents` | 문서 업로드 | multipart/form-data | 201, 400 |
| `GET` | `/documents` | 문서 목록 조회 | - | 200 |
| `GET` | `/documents/{documentId}` | 문서 상세 조회 | documentId(path) | 200 |
| `DELETE` | `/documents/{documentId}` | 문서 삭제 | documentId(path) | 204, 409 |
## 지원 단위/Application API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/applications` | 지원 단위 생성 | application/json | 201 |
| `GET` | `/applications` | 지원 단위 목록 조회 | - | 200 |
| `GET` | `/applications/{applicationId}` | 지원 단위 상세 조회 | applicationId(path) | 200 |
| `PATCH` | `/applications/{applicationId}` | 지원 단위 수정 | applicationId(path), application/json | 200 |
| `DELETE` | `/applications/{applicationId}` | 지원 단위 삭제 | applicationId(path) | 204 |
| `PUT` | `/applications/{applicationId}/sources` | 지원 단위 source 저장 | applicationId(path), application/json | 200 |
## 자소서 생성 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/applications/{applicationId}/questions` | 자소서 문항 일괄 저장 | applicationId(path), application/json | 200 |
| `GET` | `/applications/{applicationId}/questions` | 자소서 문항 목록 조회 | applicationId(path) | 200 |
| `POST` | `/applications/{applicationId}/questions/generate-answers` | 자소서 초안 생성 | applicationId(path), application/json | 200, 502, 503 |
## 면접 질문 생성 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/interview/question-sets` | 면접 질문 세트 생성 | application/json | 201 |
| `GET` | `/interview/question-sets` | 면접 질문 세트 목록 조회 | - | 200 |
| `GET` | `/interview/question-sets/{questionSetId}` | 면접 질문 세트 상세 조회 | questionSetId(path) | 200 |
## 모의 면접 세션/결과 API

| Method | Path | 설명 | 주요 입력 | 주요 응답 코드 |
|---|---|---|---|---|
| `POST` | `/interview/sessions` | 모의 면접 세션 시작 | application/json | 201, 409 |
| `GET` | `/interview/sessions` | 면접 세션 목록 조회 | - | 200 |
| `GET` | `/interview/sessions/{sessionId}` | 세션 상세 조회 | sessionId(path) | 200 |
| `POST` | `/interview/sessions/{sessionId}/answers` | 세션 답변 제출 | sessionId(path), application/json | 201, 400 |
| `POST` | `/interview/sessions/{sessionId}/complete` | 세션 종료 및 결과 생성 | sessionId(path) | 200, 502, 503 |
| `GET` | `/interview/sessions/{sessionId}/result` | 면접 결과 상세 조회 | sessionId(path) | 200 |

## 6. 핵심 제약과 검증 포인트

- GitHub private repository 접근은 OAuth 추가 동의와 적절한 scope가 있을 때만 허용한다.
- 문서 업로드는 PDF, DOCX, MD만 허용하고 파일당 최대 10MB, 기본 최대 5개 제한을 따른다.
- 자소서 문항은 1회 최대 10개까지 저장/생성한다.
- 면접 질문 생성은 1회 최대 20개까지 허용한다.
- 면접 답변은 일반 답변 기준 50자 이상 1000자 이하이고, `isSkipped=true`인 경우 예외를 둔다.
- 사용자 1인당 동시 진행 세션은 1개로 제한한다.

## 7. 오류 정책 연결

- 공통 오류 응답 구조: `docs/error-policy.md` 5장
- HTTP 상태코드 사용 기준: `docs/error-policy.md` 6장
- Validation 오류 정책: `docs/error-policy.md` 9장
- 상태 전이 오류 정책: `docs/error-policy.md` 10장
- 외부 연동 오류 정책: `docs/error-policy.md` 12장
- 재시도/멱등성 정책: `docs/error-policy.md` 13장

## 8. 이 문서 사용 방법

- 실제 구현/수정은 먼저 `openapi.yaml`을 갱신한다.
- 이 문서는 archive 비교용 요약 사본이므로, 상세 request/response schema는 `openapi.yaml`을 직접 본다.
- 화면/DB/AI 출력 스키마까지 함께 바뀌는 변경은 같은 PR에서 관련 canonical 문서를 동기화한다.
