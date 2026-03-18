---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 프론트 협업자
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-requirements-v5
applies_to: backend-api-governance
---

# API 계약 운영 규칙

## 1. 역할

이 문서는 endpoint 목록을 다시 적는 명세서가 아니다.
실제 HTTP 계약의 원본은 `openapi.yaml`이며, 이 문서는 그 계약을 어떤 원칙으로 설계하고 변경할지 정의한다.

즉,
- 필드명, enum, 요청/응답 구조, 상태코드, 예시는 `openapi.yaml`
- 변경 절차, 네이밍 규칙, 응답 원칙, 오류 매핑 원칙은 이 문서
를 따른다.

## 2. 변경 원칙

- API 계약이 바뀌면 먼저 `openapi.yaml`을 수정한다.
- 요청/응답 구조 변경은 같은 PR에서 `docs/error-policy.md`, `docs/db/schema.md`, `docs/frontend/screen-spec.md`, `docs/project/open-items.md`까지 필요한 범위를 함께 수정한다.
- 이 문서에 endpoint 목록을 중복으로 적지 않는다.
- 새 enum, 상태값, 오류 코드는 문서 선반영 없이 추가하지 않는다.

## 3. URL와 버전 규칙

- Base URL은 `/api/v1`을 사용한다.
- 리소스명은 복수형 명사를 사용한다.
- 동사는 가능한 한 HTTP Method로 표현한다.
- 정말 필요한 도메인 액션만 하위 path를 허용한다.
- 예시
  - `/github/repositories`
  - `/github/repositories/{repositoryId}/sync-commits`
  - `/applications/{applicationId}/self-intro:generate`

## 4. 인증 규칙

- 인증 우선순위는 `Authorization` 헤더가 먼저다.
- 헤더 형식은 `Bearer {apiKey} {accessToken}`를 사용한다.
- 헤더가 없을 때만 쿠키 `apiKey`, `accessToken` 폴백을 고려한다.
- 사용자 식별자는 인증 컨텍스트에서 가져오며, 요청 본문에서 `userId`를 받지 않는다.

## 5. 응답 규칙

- 성공 응답은 `success`, `data`, `meta`를 포함한다.
- 실패 응답은 `success`, `error`, `meta`를 포함한다.
- 목록 조회는 `meta.pagination`을 포함한다.
- 날짜/시간은 ISO-8601 UTC 문자열을 사용한다.
- Controller는 Entity를 직접 반환하지 않는다.
- `Map<String, Object>` 임시 응답을 금지한다.

## 6. 오류 매핑 규칙

- HTTP 상태코드는 transport 수준 의미를, `error.code`는 프론트 분기 기준을 담당한다.
- validation 오류는 가능하면 `fieldErrors`를 함께 내려준다.
- 정책 위반과 일시 실패를 구분하고, retryable 여부를 명시한다.
- 상세 분류 원칙은 `docs/error-policy.md`, 대표 코드 카탈로그는 `docs/error-codes.md`를 따른다.

## 7. 파일 업로드 규칙

- 업로드 API는 `multipart/form-data`를 사용한다.
- 허용 형식과 용량 제한은 `docs/project/open-items.md`와 `openapi.yaml`을 따른다.
- 현재 MVP 기준 허용 형식은 `PDF`, `DOCX`, `MD`, 파일당 최대 용량은 `10MB`다. 기본 업로드 수는 5개다.
- 형식 또는 용량 위반은 `DOCUMENT_INVALID_TYPE`, `DOCUMENT_FILE_TOO_LARGE` 계열 오류로 일관되게 응답한다. private repository 접근이 필요한 경우에는 OAuth scope 부족 오류를 별도로 구분한다.

## 8. 페이징, 정렬, 필터 규칙

- 기본 페이지네이션은 `page`, `size`, `sort`를 사용한다.
- `page`는 1부터 시작한다.
- 기본 `size`는 20, 최대 `size`는 100이다.
- 정렬은 `sort=createdAt,desc` 형식을 따른다.
- 허용하지 않는 정렬 키는 validation 오류로 처리한다.

## 9. OpenAPI 변경 체크리스트

API 변경 PR에서는 아래를 함께 본다.

- `openapi.yaml`이 실제 변경을 반영하는가
- 새 필드명과 enum이 기존 문서와 충돌하지 않는가
- 오류 코드가 `docs/error-policy.md`와 `docs/error-codes.md`에 반영되었는가
- 저장 구조 변경이 있으면 `docs/db/schema.md`까지 반영되었는가
- 화면 상태가 바뀌면 `docs/frontend/screen-spec.md`도 수정되었는가
- 기존에 닫힌 결정이라면 `docs/project/open-items.md`와 값이 같은가
