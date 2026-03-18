# AI 기술 면접 연습 플랫폼 공통 개발 규칙

## 1. 문서 개요

- 문서명: AI 기술 면접 연습 플랫폼 공통 개발 규칙
- 버전: v0.1
- 권장 저장 경로: `docs/backend-conventions.md`
- 작성 목적: 백엔드 4인 팀과 AI가 같은 기준으로 구현하도록 공통 개발 규칙을 정의한다.
- 적용 범위: 백엔드 API, 도메인 로직, DB 접근, 외부 연동, 테스트, PR, 문서 동기화
- 기준 문서
  - 요구사항 명세서 v0.2
  - ERD 초안 v0.1
  - DB 설계 정리 v0.1
  - API 명세서 초안 v0.1
  - AI 문서 주도 개발 협업 방식 문서

## 2. 문서 우선순위

규칙이 충돌하면 아래 우선순위를 따른다.

1. 요구사항 명세서
- 기능 범위, 선행조건, validation, 예외, 수용 기준의 원본이다.

2. API 명세서 또는 `openapi.yaml`
- HTTP 계약, 요청/응답 구조, 인증 방식, 상태코드의 원본이다.
- `openapi.yaml`이 생기면 API 명세 md보다 우선한다.

3. ERD / DB 설계 문서 / migration 파일
- 테이블 구조, 컬럼, 제약조건, 인덱스의 원본이다.
- 실제 DB 변경은 migration 파일을 최종 기준으로 본다.

4. 이 문서
- 구현 방식, 코드 구조, 책임 분리, 테스트, PR 기준의 원본이다.

5. `AGENTS.md`
- AI 작업 시 읽는 요약 규칙이다.
- 이 문서의 핵심을 짧게 요약하고 링크하는 역할로 둔다.

## 3. 기술 기준

- Language: Java
- Framework: Spring Boot
- Security: Spring Security, OAuth2 Client
- ORM: Spring Data JPA
- Validation: Bean Validation
- Database: PostgreSQL
- Cache: Redis
- External Integration: GitHub REST API, OAuth2 Provider API, AI API
- Test: JUnit5, Mockito, Testcontainers, RestAssured 또는 MockMvc

## 4. 설계 원칙

### 4.1 도메인 우선 구조를 사용한다

- 패키지는 기술 레이어 기준이 아니라 도메인 기준으로 나눈다.
- 공통 관심사는 `common`으로 모으고, 기능 로직은 각 도메인 내부에 둔다.
- 새 기능을 추가할 때는 먼저 어느 도메인 책임인지 결정한 뒤 패키지를 만든다.

권장 예시

```text
src/main/java/com/example/interviewplatform
├─ common
│  ├─ config
│  ├─ response
│  ├─ exception
│  ├─ security
│  ├─ logging
│  └─ util
├─ auth
│  ├─ controller
│  ├─ service
│  ├─ dto
│  └─ repository
├─ user
├─ github
├─ document
├─ application
├─ interview
└─ ai
```

### 4.2 `application` 중심 흐름을 유지한다

- 자소서 생성과 면접 질문 생성은 `applications` 중심 구조를 유지한다.
- `application`은 회사명, 직무, 자소서 문항, 선택한 저장소/문서를 함께 묶는 단위이다.
- 질문 세트와 실제 면접 세션은 분리한다.
- 질문 생성 이력과 실제 답변 이력을 섞지 않는다.

### 4.3 GitHub 로그인과 GitHub 연동을 분리한다

- `auth_accounts`는 로그인 계정 연결 정보이다.
- `github_connections`는 GitHub 데이터 동기화 연결 정보이다.
- Google 또는 Kakao 로그인 사용자도 GitHub를 별도로 연동할 수 있어야 한다.
- 인증 로직과 GitHub 동기화 로직을 한 클래스에 섞지 않는다.

### 4.4 MVP 범위를 넘는 추정 구현을 금지한다

- MVP 범위를 벗어나는 기능을 AI가 임의로 추가하지 않는다.
- PR, Issue 수집
- 음성/영상 면접
- 비용 로그, 비동기 잡 로그, 운영 감사 로그 상세
- 결제, 광고, 커뮤니티, 대규모 통계

후속 확장 포인트는 인터페이스나 문서 메모로만 남기고 실제 동작까지 만들지 않는다.

## 5. 패키지 및 클래스 구조 규칙

### 5.1 도메인 내부 권장 구조

각 도메인은 아래 구조를 기본으로 한다.

```text
github
├─ controller
├─ service
├─ dto
│  ├─ request
│  └─ response
├─ entity
├─ repository
├─ mapper
├─ client
└─ exception
```

### 5.2 레이어별 책임

#### Controller
- HTTP 요청/응답 처리만 담당한다.
- `@Valid` 검증과 인증 사용자 추출까지 담당한다.
- 비즈니스 로직을 넣지 않는다.
- 다른 Controller를 호출하지 않는다.
- Entity를 그대로 반환하지 않는다.

#### Facade 또는 UseCase
- 여러 Service를 조합하는 오케스트레이션이 필요할 때만 둔다.
- 예: 포트폴리오 등록, GitHub 동기화 + 문서 업로드 + 저장 결합 흐름
- 트랜잭션 경계와 외부 연동 순서를 명확하게 드러낸다.

#### Service
- 핵심 비즈니스 로직을 담당한다.
- 트랜잭션 경계는 Service에 둔다.
- 도메인 규칙 검증과 상태 변경은 Service에서 처리한다.
- 네트워크 입출력과 영속성 로직을 직접 섞지 않는다.

#### Repository
- DB 접근만 담당한다.
- 비즈니스 규칙을 넣지 않는다.
- 복잡한 조회는 메서드 이름 기반 쿼리보다 명시적 쿼리로 표현한다.

#### Entity
- 영속성 모델이다.
- JPA 매핑과 최소한의 상태 변경 메서드만 가진다.
- Request DTO, Response DTO, 외부 API 스키마 역할까지 겸하지 않는다.

#### DTO
- API 요청/응답 전용 모델이다.
- Entity를 상속하거나 포함한 채 그대로 노출하지 않는다.
- 파일 업로드, 페이지네이션, 생성 옵션 등 API 문맥에 맞게 분리한다.

#### Client
- GitHub, OAuth2 제공자, AI API 등 외부 연동 전용 컴포넌트이다.
- 외부 스키마를 그대로 도메인에 퍼뜨리지 않는다.
- 응답 매핑과 예외 변환을 책임진다.

## 6. 네이밍 규칙

### 6.1 클래스명
- Controller: `GithubRepositoryController`
- Service: `GithubSyncService`
- Facade: `PortfolioRegistrationFacade`
- Repository: `ApplicationRepository`
- Request DTO: `CreateApplicationRequest`
- Response DTO: `ApplicationDetailResponse`
- Exception: `ApplicationNotFoundException`
- ErrorCode: `ApplicationErrorCode`

### 6.2 메서드명
- 동사로 시작한다.
- 조회: `get`, `find`, `search`
- 생성: `create`
- 수정: `update`
- 삭제: `delete`, `remove`
- 동기화/생성 실행: `sync`, `generate`, `evaluate`

### 6.3 변수명
- 자바 코드는 camelCase를 사용한다.
- boolean은 `is`, `has`, `can`으로 시작한다.
- 축약어는 이미 널리 쓰이는 것만 허용한다.
- `dto`, `vo`, `temp`, `data`, `result2` 같은 모호한 이름을 금지한다.

### 6.4 패키지명
- 소문자 단수/복수 혼용 대신 도메인 명칭을 일관되게 사용한다.
- `github`, `document`, `application`, `interview`처럼 명확한 도메인명을 사용한다.
- `service.impl`, `common.utils`, `misc` 같은 모호한 패키지 생성을 금지한다.

### 6.5 DB 명명
- 테이블명과 컬럼명은 snake_case를 사용한다.
- 테이블명은 현재 설계대로 복수형을 유지한다.
- Enum 값은 소문자 스네이크 또는 현재 설계의 소문자 단어를 유지한다.
- 자바 Entity 필드는 camelCase를 사용한다.

## 7. API 구현 규칙

### 7.1 URL 규칙
- Base URL은 `/api/v1`을 사용한다.
- 리소스명은 복수형 kebab-case 또는 현재 API 명세 기준 복수형 영문을 사용한다.
- 동사는 가능한 한 HTTP Method로 표현하고, 정말 필요한 동작만 하위 path에 둔다.
- 예
  - `GET /api/v1/github/repositories`
  - `POST /api/v1/github/repositories/{repositoryId}/sync-commits`

### 7.2 요청/응답 규칙
- 모든 JSON 응답은 공통 응답 형식을 따른다.
- 성공 응답은 `success`, `data`, `meta`를 포함한다.
- 실패 응답은 `success`, `error`, `meta`를 포함한다.
- 목록 조회는 `meta.pagination`을 포함한다.
- 날짜/시간은 ISO-8601 UTC 문자열을 사용한다.

### 7.3 Controller 반환 규칙
- Controller는 반드시 Response DTO 또는 공통 응답 래퍼를 반환한다.
- Entity 직접 반환 금지
- `Map<String, Object>` 직접 반환 금지
- 예외 메시지를 Controller마다 직접 조립하지 않는다.

### 7.4 인증 사용자 처리
- 현재 로그인 사용자의 식별자는 Security Context에서 가져온다.
- 요청 본문이나 query로 `userId`를 받지 않는다.
- 사용자 소유 리소스 조회 시 path id만 믿지 말고 소유권을 검증한다.

### 7.5 페이지네이션
- 목록 조회 기본값
  - `page`: 1
  - `size`: 20
  - 최대 `size`: 100
- 정렬은 `sort=createdAt,desc` 형식을 따른다.
- 커스텀 정렬이 필요하면 허용 목록을 명시한다.

## 8. Validation 규칙

### 8.1 계층별 분리
- 형식 검증은 Request DTO + Bean Validation에서 처리한다.
- 도메인 규칙 검증은 Service에서 처리한다.
- DB 제약은 최후 방어선으로 본다.

### 8.2 HTTP 상태코드 기준
- 400 Bad Request
  - 필수 필드 누락
  - 형식 오류
  - enum 파싱 실패
- 401 Unauthorized
  - 로그인 정보 없음
  - 토큰 만료 또는 위조
- 403 Forbidden
  - 리소스 접근 권한 없음
- 404 Not Found
  - 조회 대상 리소스 없음
- 409 Conflict
  - 중복 연결, 중복 생성, 상태 충돌
- 422 Unprocessable Entity
  - 도메인 규칙 위반
  - 예: 포트폴리오 데이터 부족으로 자소서 생성 불가

### 8.3 Validation 작성 기준
- 문자열 필수값은 `@NotBlank`
- 선택 문자열 길이 제한은 `@Size`
- 숫자 범위는 `@Min`, `@Max`
- 리스트 크기는 `@Size`
- 파일 형식 검증과 GitHub URL 형식 검증은 공통 validator 또는 service helper로 재사용한다.

### 8.4 오류 메시지
- 사용자 메시지는 짧고 행동 지향적으로 작성한다.
- 내부 원인과 stack trace는 사용자 응답에 넣지 않는다.
- field error는 프론트가 바로 표시할 수 있는 수준으로 유지한다.

## 9. 트랜잭션 규칙

### 9.1 기본 원칙
- `@Transactional`은 Service 계층에만 둔다.
- 조회 전용 메서드는 `readOnly = true`를 사용한다.
- Controller, Repository, Client에 트랜잭션을 두지 않는다.

### 9.2 외부 연동과 트랜잭션 분리
- GitHub API 호출, AI API 호출, OAuth2 제공자 호출은 긴 트랜잭션 안에 넣지 않는다.
- 외부 호출 결과를 받아온 뒤 최소 범위의 DB 저장 트랜잭션을 시작한다.
- 부분 실패 가능성이 있는 흐름은 저장 상태를 명확히 남긴다.

### 9.3 상태 저장 원칙
- GitHub 동기화는 `sync_status`
- 문서 추출은 `extract_status`
- 면접 세션은 `status`
- 실패를 숨기지 말고 상태 컬럼으로 남긴다.

### 9.4 멱등성
- 재시도 가능한 API는 중복 저장에 안전해야 한다.
- unique 제약이 있는 데이터는 중복 insert 대신 upsert 또는 존재 검사 전략을 사용한다.
- 동일한 GitHub 연결/저장소/커밋 데이터는 중복 생성되지 않아야 한다.

## 10. 예외 처리 규칙

### 10.1 공통 구조
- `ErrorCode` enum
- `BusinessException`
- `ExternalIntegrationException`
- `ResourceNotFoundException`
- `GlobalExceptionHandler`

### 10.2 예외 사용 기준
- 예외는 도메인 의미를 드러내야 한다.
- `RuntimeException("error")`처럼 의미 없는 예외 사용 금지
- 외부 API 실패는 외부 예외를 그대로 던지지 말고 내부 예외로 감싼다.

### 10.3 핸들러 규칙
- 예외를 HTTP 응답으로 바꾸는 책임은 전역 예외 핸들러에 둔다.
- Controller에서 `try-catch`로 에러 응답을 직접 만드는 방식을 금지한다.
- 로그 레벨은 예외의 성격에 따라 구분한다.
  - 예상 가능한 도메인 오류: `warn`
  - 예기치 않은 서버 오류: `error`

## 11. 응답 포맷 규칙

### 11.1 성공 응답 예시

```json
{
  "success": true,
  "data": {},
  "meta": {
    "requestId": "req_01HXYZ",
    "timestamp": "2026-03-17T12:00:00Z"
  }
}
```

### 11.2 실패 응답 예시

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
    "requestId": "req_01HXYZ",
    "timestamp": "2026-03-17T12:00:00Z"
  }
}
```

### 11.3 규칙
- `message`는 사용자 표시용이다.
- 내부 디버그 상세는 로그로만 남긴다.
- `code`는 프론트와 테스트가 참조하는 안정적인 값이다.
- `requestId`는 장애 추적에 사용한다.

## 12. 보안 및 인증 규칙

### 12.1 인증 방식
- 인증 우선순위
  - 1순위: `Authorization` 헤더
  - 2순위: 쿠키 `apiKey`, `accessToken`
- 헤더 형식
  - `Authorization: Bearer {apiKey} {accessToken}`

### 12.2 권한 원칙
- 현재 사용자 본인 데이터만 조회/수정 가능해야 한다.
- 소유권 검사는 모든 사용자 리소스 API에서 필수이다.
- 관리자 기능은 별도 역할이 생기기 전까지 만들지 않는다.

### 12.3 민감정보 처리
- 액세스 토큰, 세션 키, API 키, OAuth code, 쿠키 값은 로그에 남기지 않는다.
- GitHub access token 저장 정책은 추후 보안 문서로 상세화하되, 최소한 암호화 저장 가능성을 염두에 둔다.
- 문서 원문, 추출 텍스트, 이메일은 디버그 로그에 남기지 않는다.

### 12.4 파일 업로드 보안
- MIME type과 파일 확장자를 모두 검증한다.
- 허용 형식 외 업로드를 금지한다.
- 업로드 파일명은 저장소 경로로 직접 사용하지 않는다.
- 서버 파일 경로 노출 금지

## 13. DB 및 JPA 규칙

### 13.1 엔티티 매핑
- 연관관계는 기본 `LAZY`를 사용한다.
- `EAGER` 사용 금지
- 양방향 연관관계는 정말 필요한 경우에만 둔다.
- `toString`, `equals`, `hashCode`에 연관 엔티티를 포함하지 않는다.

### 13.2 Enum 매핑
- JPA enum은 `ORDINAL`이 아니라 `STRING`을 사용한다.
- DB 설계 문서의 enum 후보와 동일한 값을 사용한다.

### 13.3 삭제 정책
- soft delete 컬럼은 MVP 기본값으로 두지 않는다.
- 사용자 탈퇴는 우선 `users.status = withdrawn`로 관리한다.
- 실제 물리 삭제는 도메인 정책이 정한 경우에만 수행한다.
- `documents`, `github_repositories`, `feedback_tags`는 참조 중이면 삭제를 막는 방향을 기본값으로 둔다.

### 13.4 변경 추적
- 주요 테이블은 `created_at`, `updated_at` 또는 이에 준하는 시각 컬럼을 유지한다.
- 낙관적 락이 필요한 도메인이 생기면 `version` 컬럼을 별도 논의한다.

### 13.5 Migration 규칙
- DB 스키마 변경은 직접 DB에서 수정하지 않는다.
- migration 파일을 통해 변경한다.
- 도구는 Flyway를 기본 권장안으로 둔다.
- 파일명 예시
  - `V1__init_schema.sql`
  - `V2__add_application_status.sql`

### 13.6 인덱스 검토 기준
- unique 제약은 문서에 정의된 항목을 그대로 반영한다.
- 조회 패턴이 있는 목록 API는 인덱스를 먼저 검토한다.
- 인덱스 추가 시
  - 어떤 API가 빨라지는지
  - 쓰기 비용이 얼마나 늘어나는지
  - 정렬 컬럼과 where 조건이 맞는지
  를 PR 설명에 적는다.

## 14. 외부 연동 규칙

### 14.1 GitHub 연동
- GitHub URL 입력과 GitHub OAuth 연동은 구분한다.
- MVP에서는 repository 목록 조회와 사용자 본인 commit 수집까지만 지원한다.
- PR, Issue 수집 로직은 넣지 않는다.
- rate limit, pagination, 권한 부족을 구분해 처리한다.

### 14.2 문서 추출
- 업로드 성공과 텍스트 추출 성공은 같은 상태가 아니다.
- 추출 실패도 저장해야 한다.
- 추출 결과가 비어 있어도 상태와 원인을 남긴다.

### 14.3 AI 연동
- AI 호출은 `ai` 도메인 또는 전용 client/service에서 관리한다.
- 프롬프트 템플릿은 코드 상수에 흩뿌리지 않는다.
- 모델명, timeout, retry, fallback 여부를 한곳에서 관리한다.
- 자소서 생성과 면접 질문 생성 프롬프트를 분리한다.

## 15. 로깅 및 관측성 규칙

### 15.1 로그 원칙
- 요청 시작/종료, 외부 연동 실패, 예기치 않은 예외를 기록한다.
- 민감정보는 마스킹하거나 기록하지 않는다.
- 로그 메시지는 검색 가능한 키워드로 작성한다.

### 15.2 requestId / traceId
- 모든 요청에 `requestId`를 생성하거나 전달받아 로그와 응답에 포함한다.
- 외부 연동 호출 시 가능하면 동일한 추적 키를 이어간다.

### 15.3 레벨 기준
- `info`: 정상 흐름의 주요 이벤트
- `warn`: 복구 가능한 오류, 사용자 입력 오류, 외부 연동 실패
- `error`: 복구 불가능하거나 예기치 않은 서버 오류

## 16. 테스트 규칙

### 16.1 테스트 분류
- Unit Test
  - 순수 비즈니스 로직, mapper, validator
- Integration Test
  - Repository, JPA 매핑, 트랜잭션, 실제 DB 연동
- API Test
  - 인증, validation, 응답 포맷, 상태코드

### 16.2 도구 기준
- Unit Test: JUnit5, Mockito
- Integration Test: Testcontainers
- API Test: RestAssured 또는 MockMvc

### 16.3 작성 기준
- 새 기능은 정상 흐름 1개 이상, 실패 흐름 1개 이상 테스트한다.
- 비즈니스 규칙이 있는 Service는 테스트 없이 머지하지 않는다.
- unique 제약, 상태 전이, 소유권 검증은 통합 테스트 우선이다.
- 외부 API는 실제 호출 대신 mock 또는 stub을 사용한다.

### 16.4 테스트 메서드 네이밍
둘 중 하나를 팀 전체가 통일해서 사용한다.

예시 1
- `createApplication_success`
- `createApplication_failWhenJobRoleIsBlank`

예시 2
- `직무가_있으면_지원단위를_생성한다`
- `직무가_없으면_지원단위_생성에_실패한다`

### 16.5 병합 기준
- 관련 테스트가 모두 통과해야 한다.
- 새로운 예외 코드가 생기면 예외 핸들러 테스트도 함께 추가한다.
- API 계약이 바뀌면 API 테스트와 명세 문서를 같이 수정한다.

## 17. Git, PR, 문서 동기화 규칙

### 17.1 브랜치 전략
- `main`: 운영 기준 브랜치
- `develop`: 통합 개발 브랜치
- `feature/*`: 기능 개발 브랜치
- `hotfix/*`: 긴급 수정 브랜치

브랜치 예시
- `feature/auth-oauth-login`
- `feature/github-commit-sync`
- `feature/interview-session-result`
- `hotfix/token-cookie-parse`

### 17.2 커밋 메시지
아래 형식을 권장한다.

```text
type(scope): summary
```

예시
- `feat(auth): add kakao oauth callback handler`
- `fix(github): prevent duplicate commit sync`
- `docs(api): update interview session response`
- `test(application): add create application integration test`

권장 type
- `feat`
- `fix`
- `docs`
- `refactor`
- `test`
- `chore`

### 17.3 PR 작성 기준
- PR에는 변경 목적을 적는다.
- 주요 변경점을 적는다.
- 테스트 여부를 적는다.
- 문서 변경 여부를 적는다.
- 스키마/API/에러코드 변경 시 영향 범위를 적는다.

### 17.4 리뷰 기준
- 최소 1인 이상 리뷰 후 머지한다.
- 공통 규칙 위반 시 머지하지 않는다.
- 이해하기 어려운 설계는 코드보다 문서 보강을 먼저 요청한다.

### 17.5 문서 동기화
다음 중 하나라도 바뀌면 같은 PR에서 문서를 함께 수정한다.

- 기능 범위
- 요청/응답 구조
- 인증 방식
- DB 컬럼/제약/인덱스
- 예외 코드
- 테스트 기준

문서 반영 대상 예시
- API 변경: API 명세서 또는 `openapi.yaml`
- DB 변경: DB 설계 문서 + migration
- 구현 규칙 변경: 이 문서 + `AGENTS.md`

## 18. AI 보조 개발 규칙

### 18.1 AI가 따라야 할 기본 원칙
- 없는 요구사항을 추정해 구현하지 않는다.
- Entity를 응답으로 직접 노출하지 않는다.
- 새 의존성을 임의로 추가하지 않는다.
- 코드 생성 시 이 문서와 API/DB 문서를 먼저 읽는다.
- 바뀐 규칙이 있으면 코드를 고치기 전에 문서를 먼저 맞춘다.

### 18.2 AI가 특히 자주 실수하는 항목
- Controller에 비즈니스 로직 넣기
- Entity를 그대로 반환하기
- 외부 API 호출을 트랜잭션 안에 넣기
- 예외를 `RuntimeException` 하나로 뭉개기
- 테스트 없이 Service 추가하기
- 문서 변경 없이 API/DB 구조를 바꾸기

### 18.3 AI 작업 전 확인 순서
1. 요구사항 명세서
2. DB 설계 문서
3. API 명세서
4. 이 문서
5. 해당 디렉터리의 `AGENTS.md`

## 19. 현재 열어둘 항목

- Java 버전 최종 확정
- Spring Boot 버전 최종 확정
- migration 도구 최종 확정
- formatter / linter / pre-commit 도구 확정
- GitHub private repository 지원 시점
- `applications.status` 세분화 여부
- 점수 산정 공식 분리 여부
- 태그 마스터 운영 방식
- 감사 로그와 생성 이력 로그 분리 여부

## 20. 작업 전 체크리스트

개발 시작 전 아래를 확인한다.

- 요구사항 범위 안의 작업인가
- API가 이미 정의되어 있는가
- DB 컬럼과 제약이 문서와 맞는가
- 예외 코드가 이미 있는가
- 소유권 검사가 필요한 API인가
- 정상/실패 테스트를 같이 추가했는가
- 문서도 함께 수정해야 하는가

## 21. 작업 완료 체크리스트

머지 전 아래를 확인한다.

- 빌드와 테스트가 통과한다.
- 공통 응답 형식과 상태코드가 규칙과 맞다.
- Entity가 외부 응답으로 노출되지 않는다.
- validation과 도메인 검증이 분리되어 있다.
- 로그에 민감정보가 없다.
- 문서 변경 사항이 누락되지 않았다.
