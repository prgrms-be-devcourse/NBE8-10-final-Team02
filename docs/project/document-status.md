---
owner: 팀 전체
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: docs-sync-product-nfr-v8
applies_to: all-docs
---

# 문서 상태 보드

이 문서는 현재 저장소의 핵심 문서 상태와 소유 범위를 한눈에 보여준다.
`reviewed`는 현재 구현 기준으로 사용 가능한 상태를 뜻하고, 코드 구현 완료를 의미하지는 않는다.

| 문서 | owner | reviewer | status | 비고 |
|---|---|---|---|---|
| `docs/product.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 서비스 방향, MVP 범위, 제품 결정 원본 |
| `docs/requirements.md` | 팀 전체 | 팀 전체 | reviewed | 기능 원본 |
| `docs/project/open-items.md` | 팀 전체 | 팀 전체 | reviewed | 닫힌 결정 인덱스 |
| `openapi.yaml` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 프론트 협업자 | reviewed | HTTP 계약의 기계 판독 원본 |
| `docs/api-spec.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 프론트 협업자 | reviewed | `openapi.yaml` 파생 요청/응답 참조 문서, 불일치 시 `openapi.yaml` 우선 |
| `docs/api-guidelines.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 프론트 협업자 | reviewed | 규칙 문서, endpoint 중복 서술 금지 |
| `docs/db/erd.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 개념 관계 원본 |
| `docs/db/schema.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 저장 구조 원본 |
| `docs/db-migration.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | migration 절차, 경로, 파일명 규칙 원본 |
| `docs/error-policy.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 프론트 협업자 | reviewed | 오류 분류 원본 |
| `docs/backend-conventions.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 구현 규칙 원본 |
| `docs/ai/prompt-templates.md` | AI 기능/생성 파이프라인 | 팀 전체 | reviewed | AI 입출력 원본 |
| `docs/frontend/screen-spec.md` | 면접 세션/서비스 흐름 + 대시보드/히스토리 | 프론트 협업자 | reviewed | 화면 상태/라우트 원본 |
| `docs/frontend/wireframes.md` | 면접 세션/서비스 흐름 + 대시보드/히스토리 | 프론트 협업자 | reviewed | 화면 보조 문서 |
| `docs/project/traceability-matrix.md` | 팀 전체 | 팀 전체 | reviewed | FR-화면-API-DB 연결표 |
| `docs/ai-collaboration.md` | 팀 전체 | 팀 전체 | reviewed | 운영 원칙 원본 |
| `docs/project/wbs.md` | 팀 전체 | 팀 전체 | reviewed | 구현 일정/선행 관계 기준 |
| `docs/security.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 인증, 인가, 업로드, AI 입력 보안 기준 |
| `docs/testing.md` | 포트폴리오 수집/외부 연동 + 품질/테스트 | 팀 전체 | reviewed | 테스트 계층, 필수 시나리오, CI 게이트 |
| `docs/observability.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 로그, 메트릭, health, 알림 기준 |
| `docs/non-functional.md` | 플랫폼/공통 기반 + 인프라/배포/관측성 | 팀 전체 | reviewed | 상위 비기능 요구사항, 품질 기준 원본 |

## 상태 정의

- `draft`: 초안, 구현 기준으로 쓰기 전
- `reviewed`: 팀 검토 완료, 현재 구현 기준으로 사용 가능
- `frozen`: 현재 스프린트 동안 변경 금지, 긴급 변경만 허용
- `deprecated`: 더 이상 원본으로 사용하지 않음
