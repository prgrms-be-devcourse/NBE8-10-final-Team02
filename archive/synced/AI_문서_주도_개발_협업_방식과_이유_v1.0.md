# AI 문서 주도 개발 협업 방식과 이유

> 이 문서는 `archive/synced/` 보관용 동기화 사본입니다.
> 운영 원칙의 현재 원본은 `docs/ai-collaboration.md`, 루트 작업 규칙 요약은 `AGENTS.md` 입니다.

## 1. 왜 문서 주도 개발이 필요한가

- 이 프로젝트는 요구사항, 화면, API, DB, AI 출력 스키마, 오류 정책이 강하게 연결되어 있다.
- 한 문서만 먼저 바꾸고 나머지를 늦게 맞추면 AI와 사람이 서로 다른 기준으로 구현하기 쉽다.
- 특히 AI는 문서에 없는 기능, 상태값, enum, 오류 코드를 추정해서 추가하는 실수를 자주 하므로 canonical 문서 우선 체계가 필요하다.

## 2. 현재 권장 구조

- 상위 원본 문서: `docs/product.md`, `docs/requirements.md`, `docs/project/open-items.md`
- HTTP 계약 원본: `openapi.yaml`
- 구현 규칙 원본: `docs/backend-conventions.md`, `docs/api-guidelines.md`
- 저장 구조 원본: `docs/db/erd.md`, `docs/db/schema.md`
- 오류 정책 원본: `docs/error-policy.md`
- AI 출력 원본: `docs/ai/prompt-templates.md`
- 화면 원본: `docs/frontend/screen-spec.md`
- 작업 요약 규칙: `AGENTS.md`

## 3. 협업 원칙

### 3.1 문서가 코드보다 먼저다
- 구현 전에 문서를 먼저 맞춘다.
- 기능, 상태값, 응답 구조, 오류 정책 변경은 문서 변경 없이 선반영하지 않는다.

### 3.2 문서마다 담당 주제를 하나로 유지한다
- 요구사항, API, DB, 화면, AI 출력, 오류 정책 문서를 섞어 쓰지 않는다.
- 한 주제의 원본 문서를 명확히 둔다.

### 3.3 이미 닫힌 결정은 다시 열어두지 않는다
- `docs/project/open-items.md`에서 닫힌 결정은 다시 미결정처럼 다루지 않는다.

### 3.4 같은 변경은 같은 PR에서 닫는다
- API, DB, 오류 코드, 화면 상태, AI 출력 스키마가 함께 바뀌면 관련 문서를 같은 변경에서 동기화한다.

### 3.5 문서는 설명, 강제는 자동화가 맡는다
- 문서는 판단 기준을 기록한다.
- 실제 강제는 테스트, 리뷰, CI, PR 체크리스트가 맡는다.

## 4. AI가 작업 전에 확인할 순서

1. `AGENTS.md`
2. `docs/requirements.md`
3. `docs/project/open-items.md`
4. `openapi.yaml`
5. `docs/api-guidelines.md`
6. `docs/db/schema.md`
7. `docs/error-policy.md`
8. 작업에 따라 `docs/frontend/screen-spec.md` 또는 `docs/ai/prompt-templates.md`

## 5. 작업 중단 조건

- 상위 문서가 비어 있거나 서로 충돌하는 경우
- 닫힌 결정과 canonical 문서가 다른 값을 가지는 경우
- 미결정 항목을 사실처럼 확정해야 하는 경우
- 새 enum, 상태값, 오류 코드를 만들어야 하는 경우
- 화면, API, DB, AI 출력 스키마 중 하나라도 연결이 비어 있는 경우

## 6. archive 문서의 위치

- `archive/originals/`: 초기 업로드 원본, 비교용
- `archive/synced/`: 현재 canonical 기준으로 다시 맞춘 참조용 동기화 사본
- 실제 구현 기준은 언제나 `docs/`와 `openapi.yaml`

## 7. 결론

문서를 많이 두는 것보다, 원본 문서를 명확히 정하고 그 문서끼리 일관되게 맞추는 것이 더 중요하다.
이 프로젝트에서는 `docs/`와 `openapi.yaml`이 원본이고, archive 문서는 회귀 비교와 설명을 위한 사본으로 유지하는 방식이 가장 안전하다.
