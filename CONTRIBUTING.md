# CONTRIBUTING.md

## 브랜치 전략

- `main`: 최종 안정 브랜치
- `develop`: 통합 개발 브랜치
- `feature/*`: 기능 개발
- `hotfix/*`: 긴급 수정

예시

- `feature/auth-oauth-login`
- `feature/github-commit-sync`
- `feature/interview-session-result`

## 커밋 메시지 규칙

형식

```text
type(scope): summary
```

예시

- `feat(auth): add kakao oauth callback handler`
- `fix(github): prevent duplicate commit sync`
- `docs(api): update interview session response`

권장 type

- `feat`
- `fix`
- `docs`
- `refactor`
- `test`
- `chore`

## 문서 우선 확인

- 구현 시작 전 `docs/requirements.md`, `docs/project/open-items.md`, `openapi.yaml`을 먼저 확인한다.
- `status: deprecated` 문서와 `archive/originals/`는 새 구현의 직접 원본으로 사용하지 않는다.

## 문서 동기화 규칙

아래가 바뀌면 같은 PR에서 관련 문서를 함께 수정한다.

- 기능 범위
- API 요청/응답 구조
- 인증 방식
- DB 컬럼/제약/인덱스
- 화면 상태/라우트
- 오류 코드와 메시지 구조
- AI 출력 스키마
- 테스트 기준

## PR 규칙

PR에는 아래를 포함한다.

- 변경 목적
- 상위 원본 문서
- 주요 변경점
- 테스트 여부
- 문서 수정 여부
- API/DB/오류 코드 영향 범위

## 문서 상태 운영

핵심 문서는 `draft`, `reviewed`, `frozen`, `deprecated` 상태를 사용한다.
현재 상태 보드는 `docs/project/document-status.md`를 따른다.

## 리뷰 기준

- 최소 1인 이상 리뷰 후 머지
- 문서와 코드가 충돌하면 원본 문서를 먼저 수정하거나 충돌을 해소한다
- 설계가 불명확하면 구현보다 문서 보강을 먼저 요청한다
- 큰 변경은 `PLANS.md` 템플릿으로 계획을 남긴다
