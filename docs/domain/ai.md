---
owner: AI 기능/생성 파이프라인
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-31
linked_issue_or_pr: docs-sync-requirements-v5
applies_to: ai-domain
---

# Domain: AI

## 목적
자소서 생성, 질문 생성, 꼬리 질문, 면접 평가를 위한 프롬프트 템플릿과 후처리 규칙을 관리한다.

## 주요 템플릿
- `ai.portfolio.summary.v1`
- `ai.self_intro.generate.v1`
- `ai.interview.questions.generate.v1`
- `ai.interview.followup.generate.v1`
- `ai.interview.evaluate.v1`
- `ai.interview.summary.v1`

## 공통 규칙
- JSON object 하나만 출력한다.
- 없는 프로젝트, 성과, 회사 정보, 저장소 내용을 만들지 않는다.
- enum, 길이 제한, 질문 수 제한을 지킨다.
- schema 검증 실패 시 템플릿별 재시도 정책을 적용한다.
- 정상 스키마를 만족한 결과만 저장한다.
- 고정 tag master 밖의 태그를 새로 만들지 않는다.

## 현재 구현 기준으로 닫힌 결정
- 자소서 분량 옵션은 `short | medium | long`만 사용한다.
- 자소서 문항 생성은 최대 10문항, 면접 질문 생성은 최대 20문항까지 허용한다.
- AI 재시도는 템플릿별 고정 횟수만 사용하고 fallback model 자동 전환은 기본 비활성화한다.
- 면접 점수는 `0~100` 정수 기준으로 저장한다.
- 약점 태그는 `feedback_tags` 마스터에서만 선택한다.
- `ai.interview.followup.generate.v1`는 `followUpQuestion | null` 응답을 사용한다.
- follow-up 생성은 skip, depth 초과, low_context일 때 `null`을 정상 결과로 허용한다.
- follow-up 생성 timeout / schema 오류는 세션 진행을 막지 않고 인터뷰 도메인의 fallback 규칙으로 넘긴다.

## 주요 품질 플래그
- `low_context`
- `weak_evidence`
- `missing_company_context`
- `document_extraction_partial`
- `duplicate_risk`
- `schema_recovered`

## 관련 문서
- `docs/ai/prompt-templates.md`
- `docs/project/open-items.md`
- `docs/non-functional.md`
- `docs/error-policy.md`
- `openapi.yaml`
