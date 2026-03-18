---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-17
linked_issue_or_pr: -
applies_to: db-schema-and-migrations
---

# DB Migration 규칙

이 문서는 DB 변경을 문서와 실제 migration 파일에 어떻게 반영할지 정의한다.

## 원칙

- 개념 관계는 `docs/db/erd.md`
- 컬럼, 제약, 인덱스는 `docs/db/schema.md`
- 실제 반영 기준은 migration 파일
- migration 도구는 Flyway를 사용한다.
- migration 파일 경로는 `backend/src/main/resources/db/migration`를 사용한다.

## 변경 절차

1. 요구사항 또는 API 변경을 확인한다.
2. `docs/db/schema.md`를 먼저 수정한다.
3. migration 파일을 작성한다.
4. 관련 테스트를 추가한다.
5. 필요 시 `openapi.yaml`, 오류 정책, 화면 문서를 같은 PR에서 함께 수정한다.

## 규칙

- destructive change는 롤백 전략 없이 바로 적용하지 않는다.
- enum 확장은 관련 원본 문서와 같은 PR에서 설명한다.
- unique 제약, foreign key, index 변경은 성능 및 충돌 가능성을 검토한다.
- 운영 DB에서 수동 변경만 하고 문서나 migration을 생략하지 않는다.
- 파일명 형식은 `V{번호}__{설명}.sql`를 사용한다.
- migration 번호는 중복 없이 증가시킨다.
- 롤백은 down migration 기본 제공보다 forward-only 원칙을 따른다.
- 복구가 필요하면 새 migration 파일로 되돌린다.
- seed 데이터는 고정 마스터 데이터만 migration으로 관리한다.
- 샘플 데이터, 테스트 데이터, 데모 데이터는 migration에 포함하지 않는다.

## 파일명 예시

- `V1__init_schema.sql`
- `V2__add_auth_tables.sql`
- `V3__seed_feedback_tags.sql`

## 이번 버전에서 확정한 항목

- migration 도구: Flyway
- migration 경로: `backend/src/main/resources/db/migration`
- 파일명 규칙: `V{번호}__{설명}.sql`
- 롤백 정책: forward-only
- seed 데이터 정책: 고정 마스터만 migration으로 관리
