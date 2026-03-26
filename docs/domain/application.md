---
owner: AI 기능/생성 파이프라인
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-24
linked_issue_or_pr: docs-sync-requirements-v5
applies_to: application-domain
---

# Domain: Application

## 목적
하나의 지원 준비 단위를 기준으로 자소서와 면접 준비 입력값을 묶는다.

## 핵심 엔티티
- `applications`
- `application_source_repositories`
- `application_source_documents`
- `application_questions`

## 핵심 규칙
- `job_role`은 필수다.
- `company_name`과 `application_type`은 nullable일 수 있다.
- 자소서 생성은 직무별 기본 템플릿을 우선 적용한다.
- 회사별 특수 문항은 사용자가 직접 추가한다.
- 자소서 문항은 한 번에 최대 10개까지 저장한다.
- 자소서 생성과 면접 질문 생성은 모두 `application`을 입력 기준으로 사용한다.
- 선택된 source와 문항 정보가 함께 저장되어야 한다.
- `status`는 `draft | ready` 두 단계로 운영한다.
- `ready`는 `job_role`, 1개 이상 source, 1개 이상 question, 모든 저장 question의 유효 답변이 준비된 경우에만 허용한다.
- 유효 답변은 `editedAnswer`를 우선 사용하고, 없으면 `generatedAnswer`를 사용한다.
- source 또는 question 변경으로 `ready` 조건을 잃으면 상태는 다시 `draft`로 본다.

## 주요 API
- `POST /applications`
- `GET /applications`
- `GET /applications/{applicationId}`
- `PATCH /applications/{applicationId}`
- `PUT /applications/{applicationId}/sources`
- `POST /applications/{applicationId}/questions`
- `POST /applications/{applicationId}/questions/generate-answers`

## 대표 오류
- `APPLICATION_NOT_FOUND`
- `APPLICATION_JOB_ROLE_REQUIRED`
- `APPLICATION_QUESTION_REQUIRED`
- `APPLICATION_SOURCE_REQUIRED`
- `APPLICATION_STATUS_CONFLICT`
- `SELF_INTRO_GENERATION_FAILED`
- `SELF_INTRO_GENERATION_TIMEOUT`
