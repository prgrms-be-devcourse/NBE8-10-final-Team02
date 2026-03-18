> 이 문서는 `archive/synced/` 보관용 동기화 사본입니다.
> 현재 구현 기준 원본은 `docs/error-policy.md` 입니다.
> 최종 구현·수정 시에는 archive 사본이 아니라 canonical 문서를 우선합니다.

# AI 기술 면접 연습 플랫폼 예외 및 오류 처리 정책

## 1. 문서 개요

- 문서명: AI 기술 면접 연습 플랫폼 예외 및 오류 처리 정책
- 버전: v0.1
- 작성 목적: 백엔드 API, 외부 연동, 데이터 처리 과정에서 발생하는 예외와 오류를 일관되게 처리하기 위한 기준을 정의한다.
- 적용 범위: 1차 MVP 기준 백엔드 API, 인증, GitHub 연동, 문서 업로드/추출, 자소서 생성, 면접 질문 생성, 모의 면접, 결과 저장 기능 전반
- 기준 문서:
  - AI 기술 면접 연습 플랫폼 요구사항 명세서 v0.2
  - AI 기술 면접 연습 플랫폼 ERD 초안 v0.1
  - AI 기술 면접 연습 플랫폼 DB 설계 정리 v0.1
  - AI 기술 면접 연습 플랫폼 비기능 요구사항 v0.1

## 2. 작성 원칙

- 본 문서는 기능 요구사항을 대체하지 않고, 기능별 예외 상황을 시스템 수준 정책으로 통합한다.
- 사용자에게 보여주는 메시지와 내부 로그 메시지는 분리한다.
- HTTP 상태코드, 공통 오류 코드, 사용자 메시지, 로그 레벨은 서로 일관되어야 한다.
- 오류가 발생해도 데이터 무결성이 깨지지 않도록 처리해야 한다.
- 외부 연동 실패와 내부 비즈니스 규칙 위반은 구분해서 다뤄야 한다.
- 새 미결정 항목이 생기면 `docs/project/open-items.md`와 함께 관리하고, 이미 닫힌 값은 다시 미결정으로 되돌리지 않는다.

## 3. 오류 처리 목표

- 프론트엔드가 예측 가능한 형태로 오류를 처리할 수 있어야 한다.
- 운영자는 오류 원인을 로그와 메트릭으로 빠르게 식별할 수 있어야 한다.
- 사용자는 자신의 행동으로 해결 가능한 오류와 시스템 내부 오류를 구분해서 이해할 수 있어야 한다.
- 부분 실패가 전체 데이터 오염으로 이어지지 않아야 한다.
- 인증, 소유권, 상태 전이 오류가 조용히 무시되면 안 된다.

## 4. 오류 분류 체계

### 4.1 분류 기준

오류는 아래 6개 범주로 분류한다.

1. 인증 오류
2. 인가/소유권 오류
3. 입력값 및 validation 오류
4. 도메인 규칙 위반 오류
5. 외부 연동 오류
6. 시스템 내부 오류

### 4.2 범주별 정의

#### 4.2.1 인증 오류

- 로그인되지 않은 요청
- 지원하지 않는 OAuth 제공자 요청
- 만료되었거나 유효하지 않은 토큰
- OAuth 콜백 파라미터 누락 또는 위조 가능성

#### 4.2.2 인가/소유권 오류

- 다른 사용자의 application, document, session에 접근하는 요청
- 수정/삭제 권한이 없는 resource 요청
- 참조는 가능하지만 소유자가 아닌 리소스 수정 요청

#### 4.2.3 입력값 및 validation 오류

- 필수 필드 누락
- 형식이 맞지 않는 URL, enum, 파일 형식
- 허용 길이 또는 허용 개수 초과
- 잘못된 page/size/sort 파라미터

#### 4.2.4 도메인 규칙 위반 오류

- 포트폴리오 데이터 없이 자소서 생성 요청
- 종료되지 않은 세션에 대한 결과 확정 요청
- 참조 중인 문서 삭제 요청
- 이미 완료된 세션에 대한 재답변 제출
- 동일 항목 중복 연결 요청

#### 4.2.5 외부 연동 오류

- OAuth 제공자 응답 실패
- GitHub API rate limit, 권한 부족, resource not found
- 문서 텍스트 추출 엔진 실패
- AI 생성 timeout, 형식 불일치, 빈 응답

#### 4.2.6 시스템 내부 오류

- 예상하지 못한 Null/State 오류
- 트랜잭션 실패
- DB 연결 문제
- 스토리지 저장 실패
- 로직 버그 또는 매핑 오류

## 5. 공통 오류 응답 규칙

### 5.1 공통 응답 형식

실패 응답은 아래 구조를 기본값으로 한다.

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
    ],
    "retryable": false
  },
  "meta": {
    "requestId": "req_01HXYZ...",
    "timestamp": "2026-03-17T12:00:00Z"
  }
}
```

### 5.2 필드 정의

- `success`: 항상 `false`
- `error.code`: 프론트와 백엔드가 공유하는 고정 오류 코드
- `error.message`: 사용자에게 직접 노출 가능한 메시지
- `error.fieldErrors`: validation 실패 상세 목록, 없으면 생략 가능
- `error.retryable`: 사용자가 재시도해도 되는지 여부
- `meta.requestId`: 로그 추적용 요청 식별자
- `meta.timestamp`: 서버 응답 시각

### 5.3 금지 사항

- 내부 예외 클래스명, SQL 메시지, stack trace를 그대로 사용자에게 노출하지 않는다.
- 외부 제공자가 반환한 민감한 상세 응답을 그대로 전달하지 않는다.
- 성공 응답 안에 실패 상태를 숨기지 않는다.
- 일부 실패를 전체 성공처럼 처리하지 않는다.

## 6. HTTP 상태코드 사용 정책

### 6.1 기본 매핑 규칙

- `400 Bad Request`
  - 요청 형식 자체가 잘못된 경우
  - validation 실패
  - 지원하지 않는 enum 또는 잘못된 파라미터 조합

- `401 Unauthorized`
  - 인증 정보 없음
  - 토큰 무효 또는 만료
  - OAuth 인증 실패

- `403 Forbidden`
  - 인증은 되었지만 소유권 또는 권한이 없는 경우

- `404 Not Found`
  - 요청한 리소스가 없거나 접근 가능한 범위에서 찾을 수 없는 경우

- `409 Conflict`
  - 중복 연결, 중복 생성, 현재 상태와 충돌하는 요청

- `422 Unprocessable Entity`
  - 요청 형식은 맞지만 도메인 규칙을 만족하지 않는 경우

- `429 Too Many Requests`
  - rate limit 초과

- `500 Internal Server Error`
  - 내부 처리 오류

- `502 Bad Gateway`
  - 외부 제공자 응답 이상

- `503 Service Unavailable`
  - 일시적인 외부 연동 장애 또는 시스템 과부하

### 6.2 404와 403 사용 기준

- 다른 사용자의 리소스에 대한 접근은 보안 관점에서 `404` 또는 `403` 중 하나로 일관되게 선택해야 한다.
- 본 프로젝트 초안에서는 소유권이 없는 리소스에 대해 기본적으로 `404`를 우선 검토한다.
- 단, 관리 기능이나 명시적 권한 모델이 생기면 `403`을 분리할 수 있다.

## 7. 사용자 메시지 정책

### 7.1 메시지 작성 원칙

- 사용자 행동으로 해결 가능한 문제는 해결 방법이 드러나게 작성한다.
- 시스템 내부 원인은 숨기고, 사용자에게 필요한 수준만 안내한다.
- 같은 종류의 오류는 같은 톤과 문체를 유지한다.
- 한글 메시지를 기본으로 한다.

### 7.2 메시지 톤 기준

- 짧고 명확하게 작성한다.
- blame 표현을 쓰지 않는다.
- 기술 내부 용어를 최소화한다.
- 필요 시 다음 행동을 제시한다.

예시
- 잘못된 예: `GithubClientException: 403 from provider`
- 좋은 예: `접근 권한이 없는 repository입니다.`

### 7.3 사용자 메시지 레벨

- 입력 수정 필요
  - 예: `지원 직무를 입력해주세요.`
- 재시도 가능
  - 예: `잠시 후 다시 시도해주세요.`
- 문의 필요
  - 예: `문제가 계속되면 관리자에게 문의해주세요.`

## 8. 내부 로그 정책

### 8.1 로그 목적

- 원인 분석
- 운영 모니터링
- 보안 추적
- 재현 가능성 확보

### 8.2 필수 로그 항목

- requestId / traceId
- userId 또는 anonymous 여부
- API path / method
- 오류 코드
- HTTP 상태코드
- 외부 연동 대상명
- 소요 시간
- 재시도 횟수

### 8.3 민감정보 로그 금지

- access token
- refresh token
- OAuth code 전체 값
- 사용자 문서 원문 전체
- 면접 답변 원문 전체
- AI 프롬프트 전문 전체

### 8.4 로그 레벨 기준

- `INFO`
  - 정상 흐름 시작/종료
  - 비정상은 아니지만 추적이 필요한 상태 변화
- `WARN`
  - 사용자 입력 오류
  - 외부 연동 일시 실패
  - 재시도 가능한 오류
- `ERROR`
  - 시스템 내부 오류
  - 데이터 저장 실패
  - 재시도 후에도 복구되지 않은 외부 연동 오류

## 9. Validation 오류 처리 정책

### 9.1 처리 원칙

- validation 오류는 서비스 로직 진입 전에 최대한 차단한다.
- 하나의 요청에 여러 필드 오류가 있으면 가능한 범위에서 함께 반환한다.
- fieldErrors는 프론트가 화면에 직접 매핑할 수 있게 단순 구조를 유지한다.

### 9.2 fieldErrors 형식

```json
[
  {
    "field": "jobRole",
    "reason": "required"
  },
  {
    "field": "questionCount",
    "reason": "out_of_range"
  }
]
```

### 9.3 대표 validation 예시

- OAuth provider 값이 허용 목록에 없음
- GitHub URL 형식이 잘못됨
- 업로드 파일 형식이 지원 범위를 벗어남
- 자소서 문항이 비어 있음
- 면접 질문 개수가 허용 범위를 초과함
- page/size가 음수 또는 최대값 초과

## 10. 도메인 상태 전이 오류 정책

### 10.1 application

- source 데이터가 없는 상태에서 생성 요청은 허용 범위를 명확히 정의해야 한다.
- 필수 입력이 누락된 상태에서 `ready`로 변경하면 안 된다.
- 삭제된 또는 존재하지 않는 source를 application에 연결하면 안 된다.

### 10.2 interview session

- `created` 또는 `in_progress` 상태의 세션만 답변 제출을 허용한다.
- `completed` 또는 `abandoned` 상태 세션에는 추가 답변 저장을 허용하지 않는다.
- 종료되지 않은 세션을 결과 확정 상태로 저장하면 안 된다.

### 10.3 document / repository

- 참조 중인 문서는 기본적으로 삭제 제한을 둔다.
- 권한이 없는 repository는 연결 또는 동기화를 허용하지 않는다.
- commit 동기화 결과가 비어 있는 경우도 정상 빈 결과인지, 식별 실패인지 구분한다.

## 11. 부분 실패 처리 정책

### 11.1 기본 원칙

- 부분 실패는 성공으로 덮지 않는다.
- 사용자가 다시 시도하거나 확인할 수 있도록 상태를 남긴다.
- 이미 정상 저장된 데이터는 가능하면 유지하되, 논리적으로 깨진 상태는 남기지 않는다.

### 11.2 대표 사례

#### 11.2.1 포트폴리오 저장

가능한 시나리오
- repository 저장 성공, commit 동기화 실패
- 파일 업로드 성공, 텍스트 추출 실패
- 일부 repository만 동기화 성공

정책
- 각 처리 단위별 상태를 분리 저장한다.
- 전체 포트폴리오 등록 완료로 단정하지 않는다.
- 사용자에게 어떤 항목이 실패했는지 요약 메시지를 제공한다.

#### 11.2.2 면접 결과 저장

가능한 시나리오
- 세션 종료 성공, 질문별 평가 저장 일부 실패
- 총점 계산 성공, 태그 저장 실패

정책
- 세션 `completed` 상태와 결과 저장은 논리적으로 일관되게 처리한다.
- 부분 저장으로 인해 총점만 있고 상세 결과가 없는 상태는 허용하지 않는다.
- 필요하면 트랜잭션 롤백 또는 결과 생성을 실패 상태로 처리한다.

## 12. 외부 연동 오류 처리 정책

### 12.1 OAuth2 오류

대표 상황
- 제공자 승인 취소
- state 불일치
- code 누락
- provider 응답 구조 이상

정책
- 사용자 메시지와 내부 로그를 분리한다.
- 콜백 파라미터 검증 실패는 즉시 인증 실패로 처리한다.
- provider 장애는 내부 인증 오류와 분리된 코드로 기록한다.

### 12.2 GitHub 오류

대표 상황
- 권한 부족
- repository not found
- rate limit 초과
- commit 식별 실패
- 사용자 계정과 연결 불일치

정책
- 조회 실패와 권한 실패를 분리한다.
- rate limit은 retryable=true로 반환할 수 있다.
- private 또는 internal repository 접근은 OAuth scope 부족과 일반 권한 부족을 구분해서 처리한다.

### 12.3 문서 처리 오류

대표 상황
- 지원하지 않는 MIME type
- 파일 크기 초과
- 스토리지 업로드 실패
- 텍스트 추출 엔진 실패
- 추출 결과 비어 있음

정책
- 업로드 실패와 추출 실패를 같은 오류로 뭉개지 않는다.
- 파일 저장 성공 후 추출 실패 시 `extract_status=failed` 상태를 남긴다.

### 12.4 AI 생성 오류

대표 상황
- timeout
- 빈 응답
- JSON/형식 불일치
- 질문 수 불일치
- 품질 부족으로 후처리 실패

정책
- 비정상 AI 응답을 정상 결과로 저장하지 않는다.
- 사용자에게는 생성 실패 또는 일시 오류 메시지를 제공한다.
- 내부 로그에는 모델명, 버전, 소요 시간, 실패 유형을 남긴다.

## 13. 재시도 및 멱등성 정책

### 13.1 재시도 가능 여부 기준

- 사용자가 같은 요청을 다시 보내도 안전한 경우에만 retryable=true를 설정한다.
- validation 오류와 도메인 규칙 위반은 기본적으로 재시도 가능 오류가 아니다.
- 외부 연동 timeout, 일시 장애, rate limit은 재시도 가능 후보이다.

### 13.2 서버 측 재시도 원칙

- 조회성 외부 호출은 제한된 횟수 재시도를 허용할 수 있다.
- 생성성 요청은 중복 저장 방지 장치 없이 자동 재시도하지 않는다.
- AI 생성 재시도는 템플릿별 최대 1~2회로 제한하고, timeout/빈 응답/형식 오류/스키마 오류에만 적용한다.
- fallback model 자동 전환은 MVP 기본값으로 비활성화한다.
- 재시도 횟수와 backoff 정책은 운영 설정으로 분리하는 것이 바람직하다.

### 13.3 중복 생성 방지

- 동일 OAuth 계정 중복 생성
- 동일 repository 중복 저장
- 동일 commit 중복 저장
- 동일 answer/tag 중복 저장

위 항목은 DB 제약조건과 서비스 레벨 검증으로 함께 방지한다.

## 14. 트랜잭션 및 롤백 정책

### 14.1 트랜잭션 필요 작업

- application 생성 + source 연결 저장
- 질문 세트 생성 + 질문 목록 저장
- 세션 종료 + 결과 저장
- 삭제 연산 중 cascade/restrict가 얽힌 작업

### 14.2 롤백 원칙

- 핵심 저장 단계 중 하나라도 실패하면 부분 완료 상태를 남기지 않도록 롤백을 우선 검토한다.
- 외부 연동이 포함된 경우 전체 롤백이 불가능하면 내부 상태를 명시적으로 실패 처리한다.
- 롤백 여부를 사용자 메시지와 내부 로그에서 구분할 필요는 없지만, 운영 로그에서는 남겨야 한다.

## 15. 삭제 및 참조 오류 정책

### 15.1 기본 원칙

- 참조 중인 리소스는 조용히 삭제하지 않는다.
- 삭제가 제한되는 경우 이유를 명확한 오류 코드로 반환한다.
- 실제 DB 정책과 API 동작이 다르면 안 된다.

### 15.2 대표 사례

- application에서 사용 중인 document 삭제 요청
- 연결된 repository 삭제 요청
- 이미 결과가 저장된 session의 직접 삭제 요청

정책
- restrict가 기본인 리소스는 `409 Conflict` 또는 `422 Unprocessable Entity`로 응답한다.
- cascade가 기본인 리소스는 문서와 구현이 동일해야 한다.

## 16. 오류 코드 설계 규칙

### 16.1 네이밍 규칙

- 형식: `도메인_상황_원인`
- 예: `DOCUMENT_INVALID_TYPE`, `INTERVIEW_SESSION_ALREADY_COMPLETED`
- 모두 대문자와 `_`를 사용한다.
- 사용자 메시지와 1:1 매핑 가능해야 한다.

### 16.2 코드 설계 원칙

- 너무 추상적인 코드 금지
  - 예: `BAD_REQUEST`, `UNKNOWN_ERROR`
- HTTP 상태코드와 중복되는 이름 금지
  - 예: `ERROR_400`
- 도메인 구분이 가능해야 한다.
- 향후 프론트 분기 처리에 사용할 수 있어야 한다.

## 17. 공통 오류 코드 초안

### 17.1 인증/회원

- `AUTH_REQUIRED`
- `AUTH_INVALID_TOKEN`
- `AUTH_EXPIRED_TOKEN`
- `AUTH_UNSUPPORTED_PROVIDER`
- `AUTH_OAUTH_CANCELLED`
- `AUTH_PROVIDER_RESPONSE_INVALID`
- `USER_WITHDRAWN`

### 17.2 GitHub 연동

- `GITHUB_CONNECTION_NOT_FOUND`
- `GITHUB_URL_INVALID`
- `GITHUB_PROFILE_NOT_FOUND`
- `GITHUB_REPOSITORY_NOT_FOUND`
- `GITHUB_REPOSITORY_FORBIDDEN`
- `GITHUB_SCOPE_INSUFFICIENT`
- `GITHUB_RATE_LIMIT_EXCEEDED`
- `GITHUB_COMMIT_SYNC_FAILED`
- `GITHUB_USER_COMMIT_NOT_IDENTIFIED`

### 17.3 문서 업로드/추출

- `DOCUMENT_INVALID_TYPE`
- `DOCUMENT_FILE_TOO_LARGE`
- `DOCUMENT_UPLOAD_FAILED`
- `DOCUMENT_EXTRACT_FAILED`
- `DOCUMENT_EXTRACT_EMPTY`
- `DOCUMENT_IN_USE`
- `DOCUMENT_NOT_FOUND`

### 17.4 application / 자소서

- `APPLICATION_NOT_FOUND`
- `APPLICATION_JOB_ROLE_REQUIRED`
- `APPLICATION_QUESTION_REQUIRED`
- `APPLICATION_SOURCE_REQUIRED`
- `APPLICATION_STATUS_CONFLICT`
- `SELF_INTRO_GENERATION_FAILED`
- `SELF_INTRO_GENERATION_TIMEOUT`
- `SELF_INTRO_RESULT_INVALID`

### 17.5 면접 질문 / 세션 / 결과

- `INTERVIEW_QUESTION_GENERATION_FAILED`
- `INTERVIEW_QUESTION_RESULT_INVALID`
- `INTERVIEW_SESSION_NOT_FOUND`
- `INTERVIEW_SESSION_ALREADY_COMPLETED`
- `INTERVIEW_SESSION_NOT_ACTIVE`
- `INTERVIEW_ANSWER_REQUIRED`
- `INTERVIEW_RESULT_GENERATION_FAILED`
- `INTERVIEW_RESULT_INCOMPLETE`

### 17.6 공통 시스템

- `RESOURCE_NOT_FOUND`
- `REQUEST_VALIDATION_FAILED`
- `RATE_LIMIT_EXCEEDED`
- `EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE`
- `INTERNAL_SERVER_ERROR`

## 18. 오류 코드 매핑 예시

| 상황 | HTTP 상태 | 오류 코드 | 사용자 메시지 예시 | retryable |
|---|---|---|---|---|
| 인증 없이 보호 API 호출 | 401 | AUTH_REQUIRED | 로그인이 필요합니다. | false |
| 지원하지 않는 OAuth 제공자 | 400 | AUTH_UNSUPPORTED_PROVIDER | 지원하지 않는 로그인 방식입니다. | false |
| GitHub URL 형식 오류 | 400 | GITHUB_URL_INVALID | 올바른 GitHub URL을 입력해주세요. | false |
| private repository scope 부족 | 403 | GITHUB_SCOPE_INSUFFICIENT | private repository 접근 권한 동의가 필요합니다. | false |
| 파일 형식 오류 | 400 | DOCUMENT_INVALID_TYPE | 업로드할 수 없는 파일 형식입니다. | false |
| 파일 크기 초과 | 400 | DOCUMENT_FILE_TOO_LARGE | 파일 용량이 허용 범위를 초과했습니다. | false |
| 문서 추출 실패 | 502 | DOCUMENT_EXTRACT_FAILED | 문서 내용 추출에 실패했습니다. | true |
| 포트폴리오 데이터 없음 | 422 | APPLICATION_SOURCE_REQUIRED | 포트폴리오 데이터를 먼저 등록해주세요. | false |
| 자소서 생성 timeout | 503 | SELF_INTRO_GENERATION_TIMEOUT | 생성 시간이 길어지고 있습니다. 잠시 후 다시 시도해주세요. | true |
| 완료된 세션에 재답변 요청 | 409 | INTERVIEW_SESSION_ALREADY_COMPLETED | 이미 종료된 면접 세션입니다. | false |
| 면접 결과 생성 실패 | 502 | INTERVIEW_RESULT_GENERATION_FAILED | 면접 결과 생성 중 오류가 발생했습니다. | true |
| 예기치 못한 서버 오류 | 500 | INTERNAL_SERVER_ERROR | 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요. | true |

## 19. 프론트엔드 연동 가이드

- 프론트는 `error.code` 기준으로 분기 처리한다.
- `error.message`는 기본 사용자 노출 메시지로 사용한다.
- `fieldErrors`가 있으면 폼 필드 에러에 우선 매핑한다.
- `retryable=true`인 경우에만 재시도 UI를 기본 제공한다.
- 같은 HTTP 상태라도 오류 코드가 다를 수 있으므로 상태코드만으로 분기하지 않는다.

## 20. 테스트 기준

### 20.1 필수 테스트 항목

- 인증 실패 응답 구조 검증
- validation 실패 시 fieldErrors 반환 검증
- 소유권 없는 resource 접근 차단 검증
- GitHub 권한 부족 / not found / rate limit 분기 검증
- 문서 업로드 실패와 추출 실패 분리 검증
- AI 생성 빈 응답 / timeout / 형식 오류 처리 검증
- 완료된 세션 재요청 차단 검증
- 참조 중인 문서 삭제 제한 검증

### 20.2 회귀 방지 포인트

- HTTP 상태코드 변경
- 오류 코드 문자열 변경
- 사용자 메시지 구조 변경
- retryable 정책 변경
- fieldErrors 구조 변경

위 항목은 프론트와 계약된 API 변경으로 간주하고 리뷰 기준을 강화한다.

## 21. 후속 고도화 항목

- 소유권 없는 리소스 접근 시 404와 403 중 최종 기준
- 문서 추출 실패를 502와 503 중 어디에 더 엄격히 고정할지
- AI timeout 시 비동기 전환 여부
- 운영 알림 임계치 수치
- 사용자에게 재시도 버튼을 자동 노출할 오류 코드 목록

## 22. 추천 다음 단계

- 공통 에러 코드 문서를 별도 `error-codes.md`로 분리할지 결정한다.
- API 명세서에 각 endpoint별 대표 오류 응답 예시를 추가한다.
- 테스트 전략 문서에 오류 시나리오 테스트 범위를 연결한다.
- 관측성 문서에서 로그 필드와 알람 기준을 구체화한다.
