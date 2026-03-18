---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 프론트 협업자
status: reviewed
last_updated: 2026-03-18
linked_issue_or_pr: -
applies_to: api-request-response-reference
---

# API Request/Response Reference

> 이 문서는 `openapi.yaml`을 빠르게 확인하기 위한 파생 참조 문서다.
> HTTP 계약의 최종 원본은 `openapi.yaml`이고, 운영 규칙 원본은 `docs/api-guidelines.md`다.
> 값이 충돌하면 항상 `openapi.yaml`을 우선한다.

## 1. 문서 목적

- API별 `request`와 `response`를 한 눈에 확인할 수 있게 정리한다.
- 구현/테스트/프론트 연동 시 `openapi.yaml`을 열기 전에 빠르게 찾는 용도로 사용한다.
- 오류 코드 카탈로그 전체를 다시 적지 않고, `request`와 성공 응답 구조를 중심으로 정리한다.
- 명시적으로 모델링된 오류 상태만 함께 적고, 상세 오류 정책은 `docs/error-policy.md`를 따른다.

## 2. 공통 규칙

- Base URL: `/api/v1`
- 인증 우선순위: `Authorization: Bearer {apiKey} {accessToken}` 헤더 우선, 없을 때만 쿠키 폴백
- 요청 Content-Type
  - 일반 JSON: `application/json`
  - 문서 업로드: `multipart/form-data`
- 성공 응답 기본 구조

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "req_...",
    "timestamp": "2026-03-17T10:30:00Z"
  }
}
```

- 실패 응답 기본 구조

```json
{
  "success": false,
  "error": {
    "code": "SOME_ERROR_CODE",
    "message": "사용자에게 노출 가능한 메시지",
    "retryable": false,
    "fieldErrors": []
  },
  "meta": {
    "requestId": "req_...",
    "timestamp": "2026-03-17T10:30:00Z"
  }
}
```

- 목록 조회 응답은 필요 시 `meta.pagination { page, size, totalElements, totalPages }`를 포함한다.
- `204 No Content` 응답은 body가 없다.
- 이 문서의 필드 표기 규칙
  - `?`는 nullable 또는 선택 필드
  - `[]`는 배열
  - `enum(...)`은 허용 값 목록

## 3. API 상세

### 3.1 인증/회원

#### `GET /auth/oauth2/{provider}/authorize`

| 항목 | 내용 |
|---|---|
| 설명 | OAuth2 로그인 시작 |
| Path | `provider` required, `enum(github, google, kakao)` |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { provider, authorizationUrl, state }` |
| Error | `400` `ErrorResponse` |

#### `GET /auth/oauth2/{provider}/callback`

| 항목 | 내용 |
|---|---|
| 설명 | OAuth2 콜백 처리 |
| Path | `provider` required, `enum(github, google, kakao)` |
| Query | `code?`, `state?`, `error?` |
| Body | 없음 |
| Success | `200`<br>`data { provider, isNewUser, user { id, displayName, email?, profileImageUrl?, status } }` |
| Error | `401` `ErrorResponse` |

#### `GET /users/me`

| 항목 | 내용 |
|---|---|
| 설명 | 내 정보 조회 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { id, displayName, email?, profileImageUrl?, status }` |
| Error | `401` `ErrorResponse` |

#### `POST /auth/logout`

| 항목 | 내용 |
|---|---|
| 설명 | 로그아웃 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { loggedOut }` |
| Error | openapi에 별도 오류 응답 미기재 |

### 3.2 GitHub 포트폴리오

#### `POST /github/connections`

| 항목 | 내용 |
|---|---|
| 설명 | GitHub 연결 생성 또는 갱신 |
| Path | 없음 |
| Query | 없음 |
| Body | `application/json`<br>`mode` required, `enum(oauth, url)`<br>`githubLogin?` string<br>`githubUserId?` int64<br>`accessScope?` string<br>`githubProfileUrl?` string |
| Success | `201`<br>`data { id, userId, githubUserId, githubLogin, accessScope?, syncStatus, connectedAt, lastSyncedAt? }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /github/repositories`

| 항목 | 내용 |
|---|---|
| 설명 | 저장소 목록 조회 |
| Path | 없음 |
| Query | `selected?` boolean<br>`visibility?` `enum(public, private, internal)`<br>`page?` integer, default `1`<br>`size?` integer, default `20` |
| Body | 없음 |
| Success | `200`<br>`data[] { id, githubRepoId, ownerLogin, repoName, fullName, htmlUrl, visibility, defaultBranch?, isSelected }`<br>`meta.pagination` 포함 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `PUT /github/repositories/selection`

| 항목 | 내용 |
|---|---|
| 설명 | 저장소 선택 상태 저장 |
| Path | 없음 |
| Query | 없음 |
| Body | `application/json`<br>`repositoryIds` required, `int64[]` |
| Success | `200`<br>`data { selectedRepositoryIds[], selectedCount }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `POST /github/repositories/{repositoryId}/sync-commits`

| 항목 | 내용 |
|---|---|
| 설명 | 저장소 커밋 동기화 시작 |
| Path | `repositoryId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `202`<br>`data { repositoryId, syncStatus, queuedAt }`<br>`syncStatus`는 `enum(queued, running, completed, failed)` |
| Error | `403` `ErrorResponse` |

참고:
- private repository는 GitHub OAuth 추가 동의와 적절한 scope가 있을 때만 지원한다.
- 현재 MVP 수집 범위는 repository와 사용자 본인 commit이다.
- 여기의 `syncStatus`는 동기화 시작 요청의 처리 상태이며, 저장되는 `github_connections.sync_status(pending, success, failed)`와는 별개다.

### 3.3 문서 업로드

#### `POST /documents`

| 항목 | 내용 |
|---|---|
| 설명 | 문서 업로드 및 텍스트 추출 시작 |
| Path | 없음 |
| Query | 없음 |
| Body | `multipart/form-data`<br>`documentType` required, `enum(resume, award, certificate, other)`<br>`file` required, binary |
| Success | `201`<br>`data { id, documentType, originalFileName, mimeType, fileSizeBytes, extractStatus, uploadedAt, extractedAt?, extractedText? }` |
| Error | `400` `ErrorResponse` |

제약:
- 허용 형식: `PDF`, `DOCX`, `MD`
- 파일당 최대 `10MB`
- 기본 업로드 수 `5개`

#### `GET /documents`

| 항목 | 내용 |
|---|---|
| 설명 | 문서 목록 조회 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data[] { id, documentType, originalFileName, mimeType, fileSizeBytes, extractStatus, uploadedAt, extractedAt?, extractedText? }`<br>예시 응답에는 `meta.pagination` 포함 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /documents/{documentId}`

| 항목 | 내용 |
|---|---|
| 설명 | 문서 상세 조회 |
| Path | `documentId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { id, documentType, originalFileName, mimeType, fileSizeBytes, extractStatus, uploadedAt, extractedAt?, extractedText? }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `DELETE /documents/{documentId}`

| 항목 | 내용 |
|---|---|
| 설명 | 문서 삭제 |
| Path | `documentId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `204` body 없음 |
| Error | `409` 설명만 명시됨<br>현재 openapi에는 이 응답의 body schema가 별도로 적혀 있지 않다. |

### 3.4 지원 단위/Application

#### `POST /applications`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 생성 |
| Path | 없음 |
| Query | 없음 |
| Body | `application/json`<br>`applicationTitle?` string<br>`companyName?` string<br>`jobRole` required, string<br>`applicationType?` string |
| Success | `201`<br>`data { id, applicationTitle?, companyName?, jobRole, status, createdAt, updatedAt, applicationType? }`<br>`status`는 `enum(draft, ready)` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /applications`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 목록 조회 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data[] { id, applicationTitle?, companyName?, jobRole, status, createdAt, updatedAt, applicationType? }`<br>`status`는 `enum(draft, ready)`<br>예시 응답에는 `meta.pagination` 포함 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /applications/{applicationId}`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 상세 조회 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { id, applicationTitle?, companyName?, jobRole, status, createdAt, updatedAt, applicationType? }`<br>`status`는 `enum(draft, ready)` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `PATCH /applications/{applicationId}`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 수정 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | `application/json`<br>`applicationTitle?` string<br>`companyName?` string<br>`jobRole?` string<br>`status?` `enum(draft, ready)`<br>`applicationType?` string |
| Success | `200`<br>`data { id, applicationTitle?, companyName?, jobRole, status, createdAt, updatedAt, applicationType? }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `DELETE /applications/{applicationId}`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 삭제 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `204` body 없음 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `PUT /applications/{applicationId}/sources`

| 항목 | 내용 |
|---|---|
| 설명 | 지원 단위 source 저장 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | `application/json`<br>`repositoryIds?` `int64[]`<br>`documentIds?` `int64[]` |
| Success | `200`<br>`data { applicationId, repositoryIds[], documentIds[], sourceCount }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `POST /applications/{applicationId}/questions`

| 항목 | 내용 |
|---|---|
| 설명 | 자소서 문항 일괄 저장 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | `application/json`<br>`questions` required, array, max `10`<br>각 항목: `questionOrder` required, `questionText` required, `toneOption?` `enum(formal, balanced, casual)`, `lengthOption?` `enum(short, medium, long)`, `emphasisPoint?` |
| Success | `200`<br>`data[] { id, questionOrder, questionText, generatedAnswer?, editedAnswer?, toneOption?, lengthOption?, emphasisPoint? }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /applications/{applicationId}/questions`

| 항목 | 내용 |
|---|---|
| 설명 | 자소서 문항 목록 조회 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data[] { id, questionOrder, questionText, generatedAnswer?, editedAnswer?, toneOption?, lengthOption?, emphasisPoint? }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `POST /applications/{applicationId}/questions/generate-answers`

| 항목 | 내용 |
|---|---|
| 설명 | 자소서 초안 생성 |
| Path | `applicationId` required, int64 |
| Query | 없음 |
| Body | `application/json`<br>`useTemplate?` boolean, default `true`<br>`regenerate?` boolean, default `false` |
| Success | `200`<br>`data { applicationId, generatedCount, regenerate, answers[] { questionId, questionText, generatedAnswer, toneOption?, lengthOption? } }` |
| Error | `502` `ErrorResponse`<br>`503` `ErrorResponse` |

제약:
- 자소서 문항 수는 1회 최대 `10개`
- 동일 문항 재생성은 허용하되, 기존 결과는 이력 보존 전제를 따른다.

### 3.5 면접 질문 세트

#### `POST /interview/question-sets`

| 항목 | 내용 |
|---|---|
| 설명 | 면접 질문 세트 생성 |
| Path | 없음 |
| Query | 없음 |
| Body | `application/json`<br>`applicationId` required, int64<br>`title?` string<br>`questionCount` required, integer, `1~20`<br>`difficultyLevel` required, `enum(easy, medium, hard)`<br>`questionTypes` required, `enum(experience, project, technical_cs, technical_stack, behavioral, follow_up)[]` |
| Success | `201`<br>`data { questionSetId, applicationId, title, questionCount, difficultyLevel, createdAt }` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /interview/question-sets`

| 항목 | 내용 |
|---|---|
| 설명 | 면접 질문 세트 목록 조회 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data[] { questionSetId, applicationId, title, questionCount, difficultyLevel, createdAt }`<br>예시 응답에는 `meta.pagination` 포함 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /interview/question-sets/{questionSetId}`

| 항목 | 내용 |
|---|---|
| 설명 | 면접 질문 세트 상세 조회 |
| Path | `questionSetId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { questionSetId, applicationId, title, questionCount, difficultyLevel, questions[] { id, questionOrder, questionType, difficultyLevel, questionText, parentQuestionId?, sourceApplicationQuestionId? }, createdAt }` |
| Error | openapi에 별도 오류 응답 미기재 |

제약:
- 질문 세트 생성 수는 1회 최대 `20개`
- 현재 질문 유형은 `experience`, `project`, `technical_cs`, `technical_stack`, `behavioral`, `follow_up`만 사용한다.

### 3.6 모의 면접 세션/결과

#### `POST /interview/sessions`

| 항목 | 내용 |
|---|---|
| 설명 | 모의 면접 세션 시작 |
| Path | 없음 |
| Query | 없음 |
| Body | `application/json`<br>`questionSetId` required, int64 |
| Success | `201`<br>`data { id, questionSetId, status, totalScore?, summaryFeedback?, startedAt?, endedAt? }`<br>`status`는 `enum(ready, in_progress, paused, completed, feedback_completed)` |
| Error | `409` `ErrorResponse` |

제약:
- 사용자 1인당 동시에 진행 가능한 활성 세션은 `1개`

#### `GET /interview/sessions`

| 항목 | 내용 |
|---|---|
| 설명 | 면접 세션 목록 조회 |
| Path | 없음 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data[] { id, questionSetId, status, totalScore?, summaryFeedback?, startedAt?, endedAt? }`<br>`status`는 `enum(ready, in_progress, paused, completed, feedback_completed)`<br>예시 응답에는 `meta.pagination` 포함 |
| Error | openapi에 별도 오류 응답 미기재 |

#### `GET /interview/sessions/{sessionId}`

| 항목 | 내용 |
|---|---|
| 설명 | 세션 상세 조회 |
| Path | `sessionId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { id, questionSetId, status, totalScore?, summaryFeedback?, startedAt?, endedAt? }`<br>`status`는 `enum(ready, in_progress, paused, completed, feedback_completed)` |
| Error | openapi에 별도 오류 응답 미기재 |

#### `POST /interview/sessions/{sessionId}/answers`

| 항목 | 내용 |
|---|---|
| 설명 | 세션 답변 제출 |
| Path | `sessionId` required, int64 |
| Query | 없음 |
| Body | `application/json`<br>`questionId` required, int64<br>`answerOrder` required, integer<br>`answerText?` string, 일반 답변이면 `50~1000자`<br>`isSkipped` required, boolean |
| Success | `201`<br>`data { sessionId, questionId, answerOrder, isSkipped, submittedAt }` |
| Error | `400` `ErrorResponse` |

제약:
- `isSkipped=false`인 경우 `answerText`는 최소 `50자` 이상이어야 한다.
- 일반 답변 최대 길이는 `1000자`다.

#### `POST /interview/sessions/{sessionId}/complete`

| 항목 | 내용 |
|---|---|
| 설명 | 세션 종료 및 결과 생성 |
| Path | `sessionId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { sessionId, status, totalScore, summaryFeedback, endedAt }`<br>`status`는 `enum(completed, feedback_completed)` |
| Error | `502` `ErrorResponse`<br>`503` `ErrorResponse` |

참고:
- 결과가 즉시 생성되면 `feedback_completed` 상태로 응답할 수 있다.

#### `GET /interview/sessions/{sessionId}/result`

| 항목 | 내용 |
|---|---|
| 설명 | 면접 결과 상세 조회 |
| Path | `sessionId` required, int64 |
| Query | 없음 |
| Body | 없음 |
| Success | `200`<br>`data { sessionId, questionSetId, status, totalScore, summaryFeedback, answers[] { answerId, questionId, questionText, answerText?, score, evaluationRationale, tags[] { tagId, tagName, tagCategory } }, startedAt?, endedAt? }` |
| Error | openapi에 별도 오류 응답 미기재 |

## 4. 확인 포인트

- 이 문서는 `request`와 `response` 빠른 확인용 참조 문서다.
- 최종 계약 검증, example, nullable, enum, 상태코드 상세는 `openapi.yaml`을 다시 확인한다.
- 공통 응답 규칙은 `docs/api-guidelines.md`, 상세 오류 정책은 `docs/error-policy.md`를 따른다.
- 현재 문서의 실제 기준 값은 `openapi.yaml`과 최신 project 문서다.
