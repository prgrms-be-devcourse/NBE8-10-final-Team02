> 이 문서는 `archive/synced/` 보관용 동기화 사본입니다.
> 현재 구현 기준 원본은 `docs/ai-collaboration.md` 입니다.
> 최종 구현·수정 시에는 archive 사본이 아니라 canonical 문서를 우선합니다.

# AI 기술 면접 연습 플랫폼 AI 문서 주도 개발 협업 운영 가이드

## 1. 목적

이 문서는 현재 프로젝트를 AI 문서 주도 개발 방식으로 운영할 때 필요한 기준 문서 우선순위, 경로 규칙, 문서 동기화 방식, 자동화 점검 기준을 정의한다.

이 프로젝트는 로그인, GitHub 연동, 문서 업로드와 추출, 자소서 생성, 면접 질문 생성, 세션 진행, 결과 저장이 하나의 흐름으로 연결된다.
그래서 기능 범위, API 계약, DB 구조, 오류 정책, AI 출력 스키마, 화면 상태가 동시에 맞아야 한다.
문서 하나만 바뀌고 나머지가 늦게 따라오면 AI와 사람이 서로 다른 기준으로 구현하게 된다.

## 2. 운영 원칙

### 2.1 문서가 코드보다 먼저다
- 기능 범위, API 계약, DB 구조, 오류 정책, AI 출력 형식이 바뀌면 코드를 수정하기 전에 문서를 먼저 맞춘다.
- 문서가 비어 있거나 충돌하면 구현보다 문서 보강을 우선한다.

### 2.2 문서마다 담당 주제를 하나로 유지한다
- `docs/requirements.md`는 기능 범위와 수용 기준의 원본이다.
- `openapi.yaml`은 HTTP 계약의 원본이다.
- `docs/db/schema.md`와 실제 migration 파일은 저장 구조의 원본이다.
- `docs/error-policy.md`는 오류 분류와 응답 원칙의 원본이다.
- `docs/ai/prompt-templates.md`는 AI 입출력 계약의 원본이다.
- `docs/frontend/screen-spec.md`는 화면 상태와 라우트의 원본이다.
- `docs/backend-conventions.md`는 구현 규칙의 원본이다.
- `AGENTS.md`는 요약 규칙만 담고 세부 본문은 다른 문서로 연결한다.

### 2.3 이미 닫힌 결정은 다시 열어두지 않는다
- `docs/project/open-items.md`는 기존 미결정 항목 중 구현 기준으로 닫힌 결정을 모아 둔 인덱스다.
- 새 작업은 이 문서를 먼저 읽고, 실제 구현은 관련 canonical 문서와 `openapi.yaml`을 기준으로 한다.
- `open-items.md`와 canonical 문서가 다르면 구현을 멈추고 문서 동기화를 먼저 한다.

### 2.4 같은 변경은 같은 PR에서 닫는다
아래가 바뀌면 관련 문서를 같은 PR에서 함께 수정한다.

- 기능 범위
- 요청/응답 구조
- 인증 방식
- DB 컬럼/제약/인덱스
- 화면 상태/라우트
- 예외 코드와 메시지 구조
- AI 출력 스키마
- 테스트 기준

### 2.5 문서는 설명, 강제는 자동화가 맡는다
- PR 템플릿, workflow, 브랜치 보호 규칙, 필수 리뷰어 지정 규칙을 함께 둔다.
- `openapi.yaml`, migration 파일, workflow 파일처럼 기계 판독 가능한 파일이 생기면 더 높은 원본으로 본다.
- CI는 버전 불일치, deprecated 경로 재참조, 핵심 문서 status 미승격을 잡아내야 한다.

## 3. 현재 canonical 구조

| 경로 | 역할 | 비고 |
|---|---|---|
| `docs/product.md` | 서비스 목적과 범위 방향성 | 사업/기획 원본 |
| `docs/requirements.md` | 기능 범위와 수용 기준 | 기능 원본 |
| `docs/project/open-items.md` | 닫힌 결정 인덱스 | 빠른 확인용 |
| `openapi.yaml` | HTTP 계약 | 기계 판독 원본 |
| `docs/api-guidelines.md` | API 설계/변경 규칙 | endpoint 중복 서술 금지 |
| `docs/db/erd.md` | 개념 관계 | 개념 모델 |
| `docs/db/schema.md` | 테이블/컬럼/제약 | 저장 구조 원본 |
| 실제 migration 파일 | 실제 DB 변경 이력 | 생기면 schema보다 우선 |
| `docs/error-policy.md` | 오류 분류와 응답 규칙 | 오류 정책 원본 |
| `docs/ai/prompt-templates.md` | 템플릿과 JSON 스키마 | AI 원본 |
| `docs/frontend/screen-spec.md` | 화면 상태/라우트/전환 | 화면 원본 |
| `docs/frontend/wireframes.md` | 시각적 보조 자료 | 표현 보조 |
| `docs/backend-conventions.md` | 구현 규칙 | 코드 구조 원본 |
| `docs/project/document-status.md` | 문서 승인 상태 | reviewed/frozen 관리 |

## 4. 원본이 아닌 경로

아래 경로는 새 구현의 직접 원본이 아니다.

- `archive/originals/`
  - 초기에 업로드된 원본 보관용
- `docs/*.md` 중 `status: deprecated`
  - 구경로 호환용 redirect 파일

canonical 문서와 AGENTS는 위 경로를 다시 참조하지 않는다.

## 5. 작업 유형별 확인 순서

### 5.1 백엔드 기능 개발
1. `docs/requirements.md`
2. `docs/project/open-items.md`
3. `openapi.yaml`
4. `docs/db/schema.md`
5. `docs/error-policy.md`
6. `docs/backend-conventions.md`
7. 필요한 경우 `docs/domain/*.md`

### 5.2 화면-상태 연계 개발
1. `docs/requirements.md`
2. `docs/project/open-items.md`
3. `docs/frontend/screen-spec.md`
4. `openapi.yaml`
5. `docs/error-policy.md`
6. `docs/frontend/wireframes.md`

### 5.3 AI 기능 개발
1. `docs/requirements.md`
2. `docs/project/open-items.md`
3. `docs/ai/prompt-templates.md`
4. `openapi.yaml`
5. `docs/db/schema.md`
6. `docs/error-policy.md`
7. `docs/backend-conventions.md`

## 6. 변경 유형별 동기화 규칙

| 변경 유형 | 반드시 같이 수정할 문서 |
|---|---|
| 인증 방식 변경 | requirements, openapi, error-policy, screen-spec |
| GitHub 연동 범위 변경 | requirements, openapi, db/schema, error-policy, screen-spec |
| 파일 업로드 형식/용량 정책 변경 | requirements, open-items, openapi, error-policy, non-functional, screen-spec |
| application 구조 변경 | requirements, openapi, db/erd, db/schema, screen-spec, backend-conventions |
| 자소서 생성 필드/분량 정책 변경 | requirements, open-items, openapi, ai/prompt-templates, screen-spec |
| 면접 점수/태그 정책 변경 | requirements, open-items, openapi, db/schema, error-policy, ai/prompt-templates |
| 오류 코드/상태 코드 변경 | error-policy, openapi, error-codes, 필요한 화면 문서 |
| AI 출력 스키마 변경 | ai/prompt-templates, openapi, db/schema, 테스트 문서 |

## 7. 작업 중단 조건

아래 중 하나라도 해당하면 코드 생성을 멈추고 문서부터 맞춘다.

- 상위 문서가 비어 있는 경우
- `docs/project/open-items.md`와 canonical 문서가 서로 다른 값을 가지는 경우
- 새 enum, 상태값, 오류 코드가 필요한데 문서에 없는 경우
- 화면, API, DB, AI 출력 스키마 중 하나라도 연결이 비어 있는 경우
- canonical 문서가 deprecated 경로나 archive 원본을 다시 참조하는 경우

## 8. 현재 닫힌 핵심 결정

상세 값은 `docs/project/open-items.md`를 따른다.

- 업로드 형식은 `PDF`, `DOCX`, `MD`
- 파일당 용량 제한은 `10MB`, 사용자당 기본 업로드 수는 5개
- GitHub URL 입력은 public repository만, private repository는 OAuth 추가 동의가 있을 때만 허용
- PR, Issue 수집은 후속 확장으로 분리
- 자소서 분량 옵션은 `short | medium | long`
- 자소서 문항 수는 최대 10개, 면접 질문 수는 최대 20개
- AI 재시도는 템플릿별 고정 횟수, fallback 자동 전환은 기본 비활성화
- 면접 점수는 `0~100` 정수
- 약점 태그는 고정 tag master 기반
- `application.status`는 `draft | ready`

## 9. 아직 남아 있는 후속 작업

이 문서 세트 기준으로 아직 사람이 마저 해야 하는 것은 아래 정도다.

- PR 생성 시 담당 리뷰어 지정 방식과 branch protection required review 규칙을 저장소 설정으로 확정
- 실제 migration 경로 생성 후 `docs/db/schema.md`와 연결
- 코드 저장소 루트의 빌드/테스트/린트 명령 확정
- `openapi.yaml` 예시 응답과 실제 DTO/Controller 반환 구조 일치 여부 점검
