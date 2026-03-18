# AI 기술 면접 연습 플랫폼 API 명세서 초안

## 1. 문서 개요

- 문서명: AI 기술 면접 연습 플랫폼 API 명세서 초안
- 버전: v0.1
- 작성 목적: 현재 확정된 요구사항, ERD, DB 설계 정리를 기준으로 백엔드 API 초안을 정의한다.
- 적용 범위: 1차 MVP API
- 기준 문서:
  - AI 기술 면접 연습 플랫폼 요구사항 명세서 v0.2
  - AI 기술 면접 연습 플랫폼 ERD 초안 v0.1
  - AI 기술 면접 연습 플랫폼 DB 설계 정리 v0.1

## 2. 설계 기준 요약

- 인증은 GitHub, Google, Kakao OAuth2 로그인을 지원한다.
- 포트폴리오 데이터는 GitHub 저장소/커밋과 업로드 문서로 구성한다.
- 자소서 생성과 면접 질문 생성 흐름은 `applications` 중심으로 묶는다.
- 면접 질문 세트와 실제 모의 면접 세션은 분리해 저장한다.
- MVP 범위에서는 GitHub repository 목록 조회와 사용자 본인 commit 수집까지만 반영한다.
- PR, Issue, 비용 로그, 비동기 잡 로그, 운영 감사 로그는 제외한다.

## 3. 공통 규칙

### 3.1 Base URL

- Base URL: `/api/v1`

### 3.2 Content-Type

- Request: `application/json`
- File Upload: `multipart/form-data`
- Response: `application/json`

### 3.3 인증 방식

- 인증 우선순위
  - 1순위: `Authorization` 헤더
  - 2순위: 쿠키 `apiKey`, `accessToken`
- 헤더 형식
  - `Authorization: Bearer {apiKey} {accessToken}`
- 인증 필요 API는 별도 표기한다.

### 3.4 공통 응답 형식

성공 응답 예시

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "req_01HXYZ...",
    "timestamp": "2026-03-17T12:00:00Z"
  }
}
```

실패 응답 예시

```json
{
  "success": false,
  "error": {
    "code": "DOCUMENT_INVALID_TYPE",
    "message": "업로드할 수 없는 파일 형식입니다.",
    "fieldErrors": [
      {
        "field": "file",
        "reason": "unsupported"
      }
    ]
  },
  "meta": {
    "requestId": "req_01HXYZ...",
    "timestamp": "2026-03-17T12:00:00Z"
  }
}
```

### 3.5 페이지네이션 규칙

목록 조회 API는 아래 규칙을 기본으로 사용한다.

- Query Parameter
  - `page`: 1부터 시작
  - `size`: 기본 20, 최대 100
  - `sort`: `createdAt,desc` 형식
- 응답 `meta.pagination`

```json
{
  "page": 1,
  "size": 20,
  "totalElements": 53,
  "totalPages": 3,
  "hasNext": true
}
```

### 3.6 날짜/시간 형식

- 모든 시각 필드는 ISO-8601 UTC 문자열을 사용한다.
- 예: `2026-03-17T12:00:00Z`

### 3.7 공통 상태 코드

- `200 OK`: 조회, 수정 성공
- `201 Created`: 생성 성공
- `202 Accepted`: 비동기 처리 시작 또는 재동기화 요청 수락
- `204 No Content`: 삭제 성공
- `400 Bad Request`: 요청 형식 오류, validation 실패
- `401 Unauthorized`: 인증 실패
- `403 Forbidden`: 권한 부족
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 중복 또는 상태 충돌
- `422 Unprocessable Entity`: 도메인 규칙 위반
- `500 Internal Server Error`: 서버 내부 오류
- `502 Bad Gateway`: 외부 OAuth/GitHub/AI 연동 오류

## 4. 공통 Enum 초안

### 4.1 user_status
- `active`
- `withdrawn`

### 4.2 auth_provider
- `github`
- `google`
- `kakao`

### 4.3 github_sync_status
- `pending`
- `success`
- `failed`

### 4.4 repository_visibility
- `public`
- `private`
- `internal`

### 4.5 document_type
- `resume`
- `award`
- `certificate`
- `other`

### 4.6 extract_status
- `pending`
- `success`
- `failed`

### 4.7 application_status
- `draft`
- `completed`

### 4.8 difficulty_level
- `easy`
- `medium`
- `hard`

### 4.9 interview_question_type
- `experience`
- `project`
- `technical_cs`
- `technical_stack`
- `behavioral`
- `follow_up`

### 4.10 interview_session_status
- `created`
- `in_progress`
- `completed`
- `abandoned`

### 4.11 feedback_tag_category
- `content`
- `structure`
- `evidence`
- `communication`
- `technical`
- `other`

## 5. 인증/회원 API

### 5.1 OAuth2 로그인 시작

- Method: `GET`
- Path: `/auth/oauth2/{provider}/authorize`
- Auth: 불필요
- Description: OAuth2 로그인 시작 URL 또는 리다이렉트를 제공한다.

#### Path Parameter
- `provider`: `github | google | kakao`

#### Response 200

```json
{
  "success": true,
  "data": {
    "provider": "github",
    "authorizeUrl": "https://provider.example.com/oauth2/authorize?..."
  },
  "meta": {}
}
```

### 5.2 OAuth2 콜백 처리

- Method: `GET`
- Path: `/auth/oauth2/{provider}/callback`
- Auth: 불필요
- Description: OAuth2 제공자의 콜백을 수신하여 로그인 처리한다.

#### Query Parameter
- `code`
- `state`
- `error` 선택

#### Response 200

```json
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "displayName": "홍길동",
      "email": "user@example.com",
      "profileImageUrl": "https://...",
      "status": "active"
    },
    "auth": {
      "provider": "github",
      "isNewUser": true,
      "apiKey": "issued-api-key",
      "accessToken": "issued-access-token"
    }
  },
  "meta": {}
}
```

### 5.3 내 정보 조회

- Method: `GET`
- Path: `/users/me`
- Auth: 필요
- Description: 현재 로그인한 사용자 기본 정보를 조회한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "id": 1,
    "displayName": "홍길동",
    "email": "user@example.com",
    "profileImageUrl": "https://...",
    "status": "active",
    "createdAt": "2026-03-17T12:00:00Z",
    "updatedAt": "2026-03-17T12:00:00Z"
  },
  "meta": {}
}
```

### 5.4 로그아웃

- Method: `POST`
- Path: `/auth/logout`
- Auth: 필요
- Description: 현재 세션 또는 토큰을 만료 처리한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "loggedOut": true
  },
  "meta": {}
}
```

## 6. GitHub 포트폴리오 API

### 6.1 GitHub 연결 생성 또는 갱신

- Method: `POST`
- Path: `/github/connections`
- Auth: 필요
- Description: GitHub OAuth 연동 또는 GitHub URL 기반 공개 계정 연결을 생성/갱신한다.

#### Request Body

```json
{
  "mode": "oauth",
  "githubLogin": "octocat",
  "githubUserId": 1001,
  "accessScope": "read:user,repo"
}
```

또는

```json
{
  "mode": "url",
  "githubProfileUrl": "https://github.com/octocat"
}
```

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 10,
    "userId": 1,
    "githubUserId": 1001,
    "githubLogin": "octocat",
    "accessScope": "read:user,repo",
    "syncStatus": "pending",
    "connectedAt": "2026-03-17T12:00:00Z",
    "lastSyncedAt": null
  },
  "meta": {}
}
```

### 6.2 내 GitHub 연결 조회

- Method: `GET`
- Path: `/github/connections/me`
- Auth: 필요
- Description: 현재 사용자에게 연결된 GitHub 정보를 조회한다.

### 6.3 GitHub 연결 해제

- Method: `DELETE`
- Path: `/github/connections/me`
- Auth: 필요
- Description: 현재 사용자의 GitHub 연결을 해제한다.
- Note: 참조 중인 repository/commit 삭제 정책은 별도 도메인 정책에 따른다.

### 6.4 GitHub URL 검증 및 저장소 프리뷰 조회

- Method: `POST`
- Path: `/github/repositories/resolve-url`
- Auth: 필요
- Description: GitHub URL을 받아 유효성 검증 후 대상 계정 또는 저장소 프리뷰를 반환한다.

#### Request Body

```json
{
  "githubUrl": "https://github.com/octocat"
}
```

#### Response 200

```json
{
  "success": true,
  "data": {
    "type": "profile",
    "githubLogin": "octocat",
    "repositories": [
      {
        "githubRepoId": 5001,
        "fullName": "octocat/hello-world",
        "htmlUrl": "https://github.com/octocat/hello-world",
        "visibility": "public",
        "defaultBranch": "main"
      }
    ]
  },
  "meta": {}
}
```

### 6.5 저장소 목록 조회

- Method: `GET`
- Path: `/github/repositories`
- Auth: 필요
- Description: 현재 사용자 GitHub 연결 기준 저장소 목록을 조회한다.

#### Query Parameter
- `selected` 선택
- `visibility` 선택
- `page`
- `size`
- `sort`

#### Response 200

```json
{
  "success": true,
  "data": [
    {
      "id": 101,
      "githubRepoId": 5001,
      "ownerLogin": "octocat",
      "repoName": "hello-world",
      "fullName": "octocat/hello-world",
      "htmlUrl": "https://github.com/octocat/hello-world",
      "visibility": "public",
      "defaultBranch": "main",
      "isSelected": true,
      "syncedAt": "2026-03-17T12:10:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

### 6.6 저장소 선택 상태 일괄 저장

- Method: `PUT`
- Path: `/github/repositories/selection`
- Auth: 필요
- Description: 자소서/면접 생성에 사용할 저장소 선택 상태를 저장한다.

#### Request Body

```json
{
  "repositoryIds": [101, 102, 103]
}
```

#### Response 200

```json
{
  "success": true,
  "data": {
    "selectedRepositoryIds": [101, 102, 103]
  },
  "meta": {}
}
```

### 6.7 저장소 커밋 동기화

- Method: `POST`
- Path: `/github/repositories/{repositoryId}/sync-commits`
- Auth: 필요
- Description: 대상 저장소의 사용자 본인 commit 데이터를 수집한다.

#### Path Parameter
- `repositoryId`

#### Response 202

```json
{
  "success": true,
  "data": {
    "repositoryId": 101,
    "syncStatus": "pending"
  },
  "meta": {}
}
```

### 6.8 저장소 커밋 목록 조회

- Method: `GET`
- Path: `/github/repositories/{repositoryId}/commits`
- Auth: 필요
- Description: 대상 저장소의 저장된 commit 목록을 조회한다.

#### Query Parameter
- `isUserCommit` 기본값 `true`
- `page`
- `size`
- `sort`

#### Response 200

```json
{
  "success": true,
  "data": [
    {
      "id": 9001,
      "githubCommitSha": "abc123",
      "authorLogin": "octocat",
      "authorName": "The Octocat",
      "authorEmail": "octocat@example.com",
      "commitMessage": "feat: add interview feedback flow",
      "isUserCommit": true,
      "committedAt": "2026-03-16T14:30:00Z"
    }
  ],
  "meta": {
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1,
      "hasNext": false
    }
  }
}
```

## 7. 문서 업로드 API

### 7.1 문서 업로드

- Method: `POST`
- Path: `/documents`
- Auth: 필요
- Content-Type: `multipart/form-data`
- Description: 이력서, 수상기록 등 문서를 업로드하고 텍스트 추출을 시작한다.

#### Form Data
- `documentType`: `resume | award | certificate | other`
- `file`: binary

#### 지원 형식 초안
- `application/pdf`
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- `text/markdown`
- `text/plain`

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 301,
    "documentType": "resume",
    "originalFileName": "resume.pdf",
    "mimeType": "application/pdf",
    "fileSizeBytes": 523412,
    "extractStatus": "pending",
    "uploadedAt": "2026-03-17T12:20:00Z"
  },
  "meta": {}
}
```

### 7.2 문서 목록 조회

- Method: `GET`
- Path: `/documents`
- Auth: 필요
- Description: 현재 사용자의 업로드 문서 목록을 조회한다.

#### Query Parameter
- `documentType` 선택
- `extractStatus` 선택
- `page`
- `size`
- `sort`

### 7.3 문서 상세 조회

- Method: `GET`
- Path: `/documents/{documentId}`
- Auth: 필요
- Description: 문서 메타데이터와 추출 결과를 조회한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "id": 301,
    "documentType": "resume",
    "originalFileName": "resume.pdf",
    "storagePath": "s3://bucket/documents/301",
    "mimeType": "application/pdf",
    "fileSizeBytes": 523412,
    "extractStatus": "success",
    "extractedText": "...",
    "uploadedAt": "2026-03-17T12:20:00Z",
    "extractedAt": "2026-03-17T12:20:10Z"
  },
  "meta": {}
}
```

### 7.4 문서 삭제

- Method: `DELETE`
- Path: `/documents/{documentId}`
- Auth: 필요
- Description: 문서를 삭제한다. 단, 참조 중인 문서이면 삭제를 제한할 수 있다.

## 8. 지원 단위 Application API

### 8.1 지원 단위 생성

- Method: `POST`
- Path: `/applications`
- Auth: 필요
- Description: 자소서 생성과 면접 질문 생성의 기준이 되는 지원 단위를 생성한다.

#### Request Body

```json
{
  "applicationTitle": "백엔드 신입 지원 초안",
  "companyName": "OpenAI Korea",
  "jobRole": "Backend Developer"
}
```

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 401,
    "applicationTitle": "백엔드 신입 지원 초안",
    "companyName": "OpenAI Korea",
    "jobRole": "Backend Developer",
    "status": "draft",
    "createdAt": "2026-03-17T12:30:00Z",
    "updatedAt": "2026-03-17T12:30:00Z"
  },
  "meta": {}
}
```

### 8.2 지원 단위 목록 조회

- Method: `GET`
- Path: `/applications`
- Auth: 필요
- Description: 현재 사용자의 지원 단위 목록을 최신순으로 조회한다.

#### Query Parameter
- `status` 선택
- `page`
- `size`
- `sort`

### 8.3 지원 단위 상세 조회

- Method: `GET`
- Path: `/applications/{applicationId}`
- Auth: 필요
- Description: 지원 단위 상세와 선택된 source, 자소서 문항 정보를 함께 조회한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "id": 401,
    "applicationTitle": "백엔드 신입 지원 초안",
    "companyName": "OpenAI Korea",
    "jobRole": "Backend Developer",
    "status": "draft",
    "selectedRepositoryIds": [101, 102],
    "selectedDocumentIds": [301],
    "questions": [
      {
        "id": 501,
        "questionOrder": 1,
        "questionText": "지원 동기를 작성해주세요.",
        "generatedAnswer": "...",
        "editedAnswer": null,
        "toneOption": "formal",
        "lengthOption": "medium",
        "emphasisPoint": "프로젝트 경험"
      }
    ],
    "createdAt": "2026-03-17T12:30:00Z",
    "updatedAt": "2026-03-17T12:40:00Z"
  },
  "meta": {}
}
```

### 8.4 지원 단위 수정

- Method: `PATCH`
- Path: `/applications/{applicationId}`
- Auth: 필요
- Description: 회사명, 직무, 제목, 상태를 수정한다.

#### Request Body

```json
{
  "applicationTitle": "백엔드 신입 지원 1차 수정",
  "companyName": "OpenAI Korea",
  "jobRole": "Backend Developer",
  "status": "completed"
}
```

### 8.5 지원 단위 삭제

- Method: `DELETE`
- Path: `/applications/{applicationId}`
- Auth: 필요
- Description: 지원 단위를 삭제한다. 자소서 문항, source 연결, 질문 세트도 함께 삭제될 수 있다.

### 8.6 지원 단위 source 저장

- Method: `PUT`
- Path: `/applications/{applicationId}/sources`
- Auth: 필요
- Description: 자소서/면접 질문 생성에 사용할 저장소와 문서 선택값을 저장한다.

#### Request Body

```json
{
  "repositoryIds": [101, 102],
  "documentIds": [301]
}
```

#### Response 200

```json
{
  "success": true,
  "data": {
    "applicationId": 401,
    "repositoryIds": [101, 102],
    "documentIds": [301]
  },
  "meta": {}
}
```

## 9. 자소서 생성 API

### 9.1 자소서 문항 일괄 생성 또는 저장

- Method: `POST`
- Path: `/applications/{applicationId}/questions`
- Auth: 필요
- Description: 자소서 문항 목록을 생성하거나 저장한다.

#### Request Body

```json
{
  "questions": [
    {
      "questionOrder": 1,
      "questionText": "지원 동기를 작성해주세요.",
      "toneOption": "formal",
      "lengthOption": "medium",
      "emphasisPoint": "프로젝트 경험"
    },
    {
      "questionOrder": 2,
      "questionText": "본인의 강점을 작성해주세요.",
      "toneOption": "formal",
      "lengthOption": "medium",
      "emphasisPoint": "협업"
    }
  ]
}
```

### 9.2 자소서 문항 목록 조회

- Method: `GET`
- Path: `/applications/{applicationId}/questions`
- Auth: 필요
- Description: 지원 단위에 저장된 자소서 문항과 생성 결과를 조회한다.

### 9.3 자소서 초안 생성

- Method: `POST`
- Path: `/applications/{applicationId}/questions/generate-answers`
- Auth: 필요
- Description: 저장된 자소서 문항과 선택 source를 기반으로 문항별 생성 답변을 만든다.

#### Request Body

```json
{
  "useTemplate": true,
  "regenerate": false
}
```

#### Response 200

```json
{
  "success": true,
  "data": {
    "applicationId": 401,
    "generatedQuestions": [
      {
        "id": 501,
        "questionOrder": 1,
        "questionText": "지원 동기를 작성해주세요.",
        "generatedAnswer": "저는...",
        "editedAnswer": null
      },
      {
        "id": 502,
        "questionOrder": 2,
        "questionText": "본인의 강점을 작성해주세요.",
        "generatedAnswer": "저의 강점은...",
        "editedAnswer": null
      }
    ]
  },
  "meta": {}
}
```

### 9.4 자소서 문항 개별 수정

- Method: `PATCH`
- Path: `/applications/{applicationId}/questions/{questionId}`
- Auth: 필요
- Description: 생성 결과 또는 사용자 수정본을 저장한다.

#### Request Body

```json
{
  "editedAnswer": "사용자가 수정한 최종 답변",
  "toneOption": "formal",
  "lengthOption": "long",
  "emphasisPoint": "협업과 문제 해결"
}
```

## 10. 면접 질문 생성 API

### 10.1 면접 질문 세트 생성

- Method: `POST`
- Path: `/interview/question-sets`
- Auth: 필요
- Description: 지원 단위를 기준으로 면접 질문 세트를 생성한다.

#### Request Body

```json
{
  "applicationId": 401,
  "title": "OpenAI Korea 백엔드 예상 질문",
  "questionCount": 10,
  "difficultyLevel": "medium",
  "questionTypes": [
    "experience",
    "technical_cs",
    "technical_stack"
  ]
}
```

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 601,
    "userId": 1,
    "applicationId": 401,
    "title": "OpenAI Korea 백엔드 예상 질문",
    "questionCount": 10,
    "difficultyLevel": "medium",
    "questionTypes": [
      "experience",
      "technical_cs",
      "technical_stack"
    ],
    "questions": [
      {
        "id": 701,
        "questionOrder": 1,
        "questionType": "experience",
        "difficultyLevel": "medium",
        "questionText": "가장 어려웠던 프로젝트 문제 해결 경험을 설명해주세요.",
        "parentQuestionId": null,
        "sourceApplicationQuestionId": 501
      }
    ],
    "createdAt": "2026-03-17T12:50:00Z"
  },
  "meta": {}
}
```

### 10.2 면접 질문 세트 목록 조회

- Method: `GET`
- Path: `/interview/question-sets`
- Auth: 필요
- Description: 사용자의 질문 세트 목록을 조회한다.

#### Query Parameter
- `applicationId` 선택
- `page`
- `size`
- `sort`

### 10.3 면접 질문 세트 상세 조회

- Method: `GET`
- Path: `/interview/question-sets/{questionSetId}`
- Auth: 필요
- Description: 질문 세트와 질문 상세를 조회한다.

## 11. 모의 면접 세션 API

### 11.1 세션 시작

- Method: `POST`
- Path: `/interview/sessions`
- Auth: 필요
- Description: 질문 세트를 기준으로 모의 면접 세션을 시작한다.

#### Request Body

```json
{
  "questionSetId": 601
}
```

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 801,
    "userId": 1,
    "questionSetId": 601,
    "status": "created",
    "startedAt": "2026-03-17T13:00:00Z"
  },
  "meta": {}
}
```

### 11.2 세션 상세 조회

- Method: `GET`
- Path: `/interview/sessions/{sessionId}`
- Auth: 필요
- Description: 세션 상태, 질문 목록, 현재까지의 답변을 조회한다.

### 11.3 세션 답변 제출

- Method: `POST`
- Path: `/interview/sessions/{sessionId}/answers`
- Auth: 필요
- Description: 특정 질문에 대한 답변을 저장한다.

#### Request Body

```json
{
  "questionId": 701,
  "answerOrder": 1,
  "answerText": "저는 프로젝트에서 ...",
  "isSkipped": false
}
```

#### Response 201

```json
{
  "success": true,
  "data": {
    "id": 901,
    "sessionId": 801,
    "questionId": 701,
    "answerOrder": 1,
    "answerText": "저는 프로젝트에서 ...",
    "isSkipped": false,
    "createdAt": "2026-03-17T13:03:00Z"
  },
  "meta": {}
}
```

### 11.4 세션 질문 건너뛰기

- Method: `POST`
- Path: `/interview/sessions/{sessionId}/skip`
- Auth: 필요
- Description: 특정 질문을 건너뛰기 처리한다.

#### Request Body

```json
{
  "questionId": 702,
  "answerOrder": 2
}
```

### 11.5 세션 종료 및 결과 생성

- Method: `POST`
- Path: `/interview/sessions/{sessionId}/complete`
- Auth: 필요
- Description: 세션을 종료하고 평가 결과를 생성 및 저장한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "sessionId": 801,
    "status": "completed",
    "totalScore": 82,
    "summaryFeedback": "기술 설명은 좋았지만 근거 제시가 약했습니다.",
    "endedAt": "2026-03-17T13:15:00Z"
  },
  "meta": {}
}
```

## 12. 면접 결과/히스토리 API

### 12.1 면접 세션 목록 조회

- Method: `GET`
- Path: `/interview/sessions`
- Auth: 필요
- Description: 사용자의 면접 세션 목록을 조회한다.

#### Query Parameter
- `status` 선택
- `page`
- `size`
- `sort`

### 12.2 면접 결과 상세 조회

- Method: `GET`
- Path: `/interview/sessions/{sessionId}/result`
- Auth: 필요
- Description: 세션 총점, 질문별 평가, 태그, 근거를 조회한다.

#### Response 200

```json
{
  "success": true,
  "data": {
    "sessionId": 801,
    "questionSetId": 601,
    "status": "completed",
    "totalScore": 82,
    "summaryFeedback": "기술 설명은 좋았지만 근거 제시가 약했습니다.",
    "answers": [
      {
        "answerId": 901,
        "questionId": 701,
        "questionText": "가장 어려웠던 프로젝트 문제 해결 경험을 설명해주세요.",
        "answerText": "저는 프로젝트에서 ...",
        "score": 80,
        "evaluationRationale": "상황 설명은 좋았으나 수치 근거가 부족함",
        "tags": [
          {
            "tagId": 1,
            "tagName": "근거 부족",
            "tagCategory": "evidence"
          }
        ]
      }
    ],
    "startedAt": "2026-03-17T13:00:00Z",
    "endedAt": "2026-03-17T13:15:00Z"
  },
  "meta": {}
}
```

## 13. 공통 에러 코드 초안

- `AUTH_UNSUPPORTED_PROVIDER`
- `AUTH_REQUIRED`
- `AUTH_OAUTH_CANCELLED`
- `AUTH_PROVIDER_RESPONSE_INVALID`
- `USER_WITHDRAWN`
- `GITHUB_CONNECTION_NOT_FOUND`
- `GITHUB_URL_INVALID`
- `GITHUB_REPOSITORY_FORBIDDEN`
- `GITHUB_REPOSITORY_NOT_FOUND`
- `GITHUB_COMMIT_SYNC_FAILED`
- `DOCUMENT_INVALID_TYPE`
- `DOCUMENT_FILE_TOO_LARGE`
- `DOCUMENT_EXTRACT_FAILED`
- `APPLICATION_NOT_FOUND`
- `APPLICATION_JOB_ROLE_REQUIRED`
- `APPLICATION_QUESTION_REQUIRED`
- `APPLICATION_SOURCE_REQUIRED`
- `SELF_INTRO_GENERATION_FAILED`
- `INTERVIEW_QUESTION_GENERATION_FAILED`
- `INTERVIEW_SESSION_NOT_FOUND`
- `INTERVIEW_SESSION_ALREADY_COMPLETED`
- `INTERVIEW_ANSWER_REQUIRED`
- `INTERVIEW_RESULT_GENERATION_FAILED`

## 14. 미결정 항목 및 후속 보완 사항

### 14.1 요구사항/DB와 연결된 미결정 항목

- 업로드 파일 최대 용량 수치
- GitHub private repository 지원 범위
- `applications.status` 세분화 여부
- 면접 점수 산정 상세 공식
- 약점 태그 마스터 운영 방식
- 자소서 생성 이력 버전 관리 테이블 분리 여부

### 14.2 API 상세 설계 시 후속 결정할 항목

- OAuth2 콜백 이후 프론트 리다이렉트 규칙
- 공통 응답 포맷 최종 확정
- validation 실패 응답 구조 상세화
- 목록 조회의 정렬 필드 제한
- 문서 삭제 시 참조 관계 처리 방식
- 실시간 꼬리 질문을 질문 세트에 영구 저장할지 여부
- GitHub URL 입력 모드에서 공개 저장소만 지원할지 여부
- 비동기 처리 API를 polling 방식으로 볼지 webhook/event 방식으로 볼지 여부

## 15. 추천 다음 단계

- 이 문서를 기준으로 `openapi.yaml` 초안으로 변환한다.
- 공통 에러 코드 문서와 응답 규칙 문서를 분리한다.
- 이후 프론트와 협업을 위해 화면별 요청/응답 예시를 추가한다.
