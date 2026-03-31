---
owner: 면접 세션/서비스 흐름 + 대시보드/히스토리
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-31
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
- `interview_session_questions`
- `interview_answers`
- `feedback_tags`
- `interview_answer_tags`

## 핵심 규칙
- 질문 세트와 실제 세션은 분리한다.
- 세션 시작 시 질문 세트 질문을 세션 전용 질문 스냅샷으로 복사한다.
- dynamic follow-up은 질문 세트 원본이 아니라 세션 전용 질문에만 추가한다.
- 일반 답변 저장과 dynamic follow-up 생성은 같은 요청으로 묶지 않는다.
- v1 dynamic follow-up 생성 시도는 답변 저장 직후의 세션 상세 재조회 경로에서 best-effort로 1회 수행한다.
- 같은 부모 답변에 대한 dynamic follow-up 생성 결과는 최대 1개만 확정한다.
- 부모 세션 질문에 child 세션 질문이 이미 있으면 추가 dynamic follow-up을 생성하지 않고 처리 완료로 본다.
- 답변별 follow-up 생성 처리 완료 여부는 `interview_answers.followup_resolved_at`로 기록한다.
- 질문 생성은 1회 최대 20개까지 허용한다.
- 질문 세트 편집은 세션 시작 전까지만 허용한다.
- 세션이 한 번이라도 시작된 질문 세트에 대한 편집 요청은 `INTERVIEW_QUESTION_SET_NOT_EDITABLE`로 거절한다.
- 질문 세트 편집 1차 범위는 질문 개별 삭제와 수동 질문 추가다.
- 질문 세트는 최소 1개 질문을 유지해야 하므로 마지막 남은 질문 삭제는 허용하지 않는다.
- 질문 삭제는 `hard delete`로 처리하고, 삭제 후 남은 질문의 `question_order`는 서버가 빈 번호 없이 `1..N`으로 재정렬한다.
- 수동 질문 추가 1차 범위는 독립 질문만 허용하며 `follow_up`, `parentQuestionId`는 받지 않는다.
- 질문 세트 편집 시 `question_order`는 클라이언트가 직접 정하지 않고 서버가 관리한다.
- 실사용 세션은 3개 이상 20개 이하 질문으로 진행한다.
- 세션 시작은 현재 사용자 소유 질문 세트에 대해서만 허용한다.
- 질문 수 `3..20` 검증은 편집 시점이 아니라 세션 시작 시점에 수행한다.
- 질문 수가 `3..20` 범위를 벗어난 질문 세트는 `REQUEST_VALIDATION_FAILED`로 세션 시작을 거절한다.
- `ready` 상태는 시작 전 상태이며 답변 제출을 허용하지 않는다.
- 답변 제출은 현재 사용자 소유 세션과 해당 세션의 현재 세션 질문에 대해서만 허용한다.
- 답변 제출 요청의 `questionId`는 세션 질문 id이며, `answerOrder`는 다음 제출 순번이어야 한다.
- 답변 제출 요청의 `questionId`, `answerOrder`가 현재 세션 질문과 다음 제출 순번에 맞지 않으면 `REQUEST_VALIDATION_FAILED`로 거절한다.
- `in_progress` 상태의 세션만 일반 답변 제출을 허용한다.
- 답변 제출 API는 답변 저장까지만 담당하고, follow-up 생성 성공/실패 여부를 직접 응답하지 않는다.
- 명시적 `pause`는 `in_progress` 상태에서만 허용한다.
- `paused` 상태는 재개 후 `in_progress`로 전환한 뒤 답변을 제출한다.
- 명시적 `resume`은 `paused` 상태에서만 허용한다.
- `pause/resume`은 상태 필드 일반 수정이 아니라 명시적 액션 API로 처리한다.
- 현재 상태에서 허용되지 않는 `pause/resume` 요청은 `INTERVIEW_SESSION_STATUS_CONFLICT`로 거절한다.
- `completed`, `feedback_completed` 세션에는 추가 답변을 허용하지 않는다.
- 명시적 `complete`는 `in_progress`, `paused` 상태에서만 허용한다.
- 미답변 질문이 남아 있으면 `REQUEST_VALIDATION_FAILED`로 세션 종료를 거절한다.
- `completed`, `feedback_completed` 세션의 종료 재요청은 `INTERVIEW_SESSION_ALREADY_COMPLETED`로 거절한다.
- `complete`에서 `INTERVIEW_RESULT_INCOMPLETE`, `INTERVIEW_RESULT_GENERATION_FAILED`, `EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE`가 반환돼도 세션 종료 기록은 `completed` 상태로 유지한다.
- 결과 상세 조회는 `feedback_completed` 상태에서만 성공한다.
- 결과 상세 조회 대상이 없거나 현재 사용자 소유가 아니면 `RESOURCE_NOT_FOUND`를 반환한다.
- 결과 상세 조회 시 세션이 `completed` 상태면 `INTERVIEW_RESULT_INCOMPLETE`를 반환한다.
- 실제로 답변이 저장된 dynamic follow-up은 `complete` 미답변 검증, 결과 생성 입력, 결과 응답, 히스토리 상세 기록에서 일반 질문과 동일하게 포함한다.
- v1의 결과 재시도는 `POST /interview/sessions/{sessionId}/complete` 재전송이 아니라 `GET /interview/sessions/{sessionId}/result` 재확인 흐름으로 처리한다.
- 전용 결과 재생성 endpoint는 이 단계에서 열지 않고 별도 이슈로 분리한다.
- 현재 질문은 `answerCount + 1` 고정 순번이 아니라, 답변이 없는 세션 질문 중 가장 작은 `question_order`로 계산한다.
- 세션 상세 재조회 시점에 직전 답변 기준 pending follow-up 생성 대상이 있으면 AI 생성을 먼저 시도한 뒤 현재 질문을 계산한다.
- follow-up AI가 `null`, timeout, schema 오류를 반환해도 세션은 멈추지 않고 다음 기본 질문으로 진행한다.
- 세션 상세 조회는 복원 화면과 히스토리 상세의 기본 정보 영역 기준으로 `currentQuestion`, 진행률 계산용 count, `resumeAvailable`, `lastActivityAt`를 함께 반환한다.
- 세션 상세의 `currentQuestion.id`, 답변 제출의 `questionId`, 결과 응답의 `answers[].questionId`는 모두 세션 질문 id를 사용한다.
- 히스토리 상세 v1은 전용 backend endpoint를 추가하지 않고 `GET /interview/sessions/{sessionId}`와 `GET /interview/sessions/{sessionId}/result`를 함께 재사용한다.
- 히스토리 상세의 질문/답변/피드백 전체 기록은 `result` 성공 응답이 있을 때만 그린다.
- `completed` 상태로 남아 있는 세션은 히스토리에서도 결과 미준비 상태로 해석하고 `GET /interview/sessions/{sessionId}/result` 재확인 흐름으로 연결한다.
- 건너뛰기 아닌 일반 답변이 비어 있으면 `INTERVIEW_ANSWER_REQUIRED`로 거절한다.
- 일반 답변은 50자 이상 1000자 이하로 검증하고, 건너뛰기는 예외로 처리한다.
- 사용자 1명당 동시에 진행 가능한 활성 세션은 1개다.
- 활성 세션은 `in_progress + paused`로 본다.
- 활성 세션이 있으면 새 세션 시작보다 기존 세션 복귀 또는 재개를 우선한다.
- 세션 목록 조회는 현재 사용자 세션만 반환한다.
- `GET /interview/sessions`는 활성 세션(`in_progress`, `paused`)이 있으면 응답 배열의 가장 앞에 두고, 나머지 과거 세션은 `startedAt` 최신순으로 같은 배열에 반환한다.
- 히스토리 목록 조회 v1은 별도 상태 필터, pagination, 정렬 query parameter 없이 현재 사용자 전체 목록 응답으로 시작한다.
- 자동 일시정지 v1은 스케줄러보다 `lastActivityAt` 기반 요청 시점 전이로 처리한다.
- v1 자동 일시정지 조건 평가는 최소 `GET /interview/sessions/{sessionId}`, `POST /interview/sessions/{sessionId}/answers`, `POST /interview/sessions/{sessionId}/resume` 진입 시점에 수행한다.
- `lastActivityAt`는 `interview_sessions.last_activity_at`에 저장한다.
- `lastActivityAt`는 세션 생성, 답변 제출 성공, 재개 성공 시점에 갱신한다.
- 세션 완료와 결과 저장은 논리적으로 일관되게 처리해야 한다.

## 주요 API
- `POST /interview/question-sets`
- `GET /interview/question-sets/{questionSetId}`
- `POST /interview/question-sets/{questionSetId}/questions`
- `DELETE /interview/question-sets/{questionSetId}/questions/{questionId}`
- `POST /interview/sessions`
- `GET /interview/sessions`
- `GET /interview/sessions/{sessionId}`
- `POST /interview/sessions/{sessionId}/pause`
- `POST /interview/sessions/{sessionId}/resume`
- `POST /interview/sessions/{sessionId}/answers`
- `POST /interview/sessions/{sessionId}/complete`
- `GET /interview/sessions/{sessionId}/result`

## 대표 오류
- `INTERVIEW_QUESTION_GENERATION_FAILED`
- `INTERVIEW_QUESTION_SET_NOT_EDITABLE`
- `INTERVIEW_SESSION_ALREADY_ACTIVE`
- `INTERVIEW_SESSION_ALREADY_COMPLETED`
- `INTERVIEW_SESSION_STATUS_CONFLICT`
- `INTERVIEW_ANSWER_REQUIRED`
- `INTERVIEW_ANSWER_TOO_SHORT`
- `INTERVIEW_RESULT_INCOMPLETE`
- `INTERVIEW_RESULT_GENERATION_FAILED`
