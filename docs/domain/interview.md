---
owner: 면접 세션/서비스 흐름 + 대시보드/히스토리
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-18
linked_issue_or_pr: docs-sync-requirements-v5
applies_to: interview-domain
---

# Domain: Interview

## 목적
질문 세트 생성, 세션 진행, 답변 저장, 결과 저장, 히스토리 조회를 담당한다.

## 핵심 엔티티
- `interview_question_sets`
- `interview_questions`
- `interview_sessions`
- `interview_answers`
- `feedback_tags`
- `interview_answer_tags`

## 핵심 규칙
- 질문 세트와 실제 세션은 분리한다.
- 질문 생성은 1회 최대 20개까지 허용한다.
- 실사용 세션은 3개 이상 20개 이하 질문으로 진행한다.
- `ready` 상태는 시작 전 상태이며 답변 제출을 허용하지 않는다.
- `in_progress` 상태의 세션만 일반 답변 제출을 허용한다.
- `paused` 상태는 재개 후 `in_progress`로 전환한 뒤 답변을 제출한다.
- `completed`, `feedback_completed` 세션에는 추가 답변을 허용하지 않는다.
- 일반 답변은 50자 이상 1000자 이하로 검증하고, 건너뛰기는 예외로 처리한다.
- 사용자 1명당 동시에 진행 가능한 활성 세션은 1개다.
- 세션 완료와 결과 저장은 논리적으로 일관되게 처리해야 한다.

## 주요 API
- `POST /interview/question-sets`
- `GET /interview/question-sets/{questionSetId}`
- `POST /interview/sessions`
- `POST /interview/sessions/{sessionId}/answers`
- `POST /interview/sessions/{sessionId}/complete`
- `GET /interview/sessions/{sessionId}/result`

## 대표 오류
- `INTERVIEW_QUESTION_GENERATION_FAILED`
- `INTERVIEW_SESSION_ALREADY_ACTIVE`
- `INTERVIEW_SESSION_ALREADY_COMPLETED`
- `INTERVIEW_ANSWER_REQUIRED`
- `INTERVIEW_ANSWER_TOO_SHORT`
- `INTERVIEW_RESULT_GENERATION_FAILED`
