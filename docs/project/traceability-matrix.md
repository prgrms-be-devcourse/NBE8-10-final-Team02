---
owner: 팀 전체
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-26
linked_issue_or_pr: feat/portfolio-readiness-dashboard
applies_to: fr-screen-api-db-ai-test
---

# FR 기준 추적 매트릭스

이 문서는 요구사항, 화면, API, DB, AI 템플릿, 테스트의 연결을 한 번에 추적하기 위한 최소 연결표다.

| FR | 기능 | 화면 | API path | 주요 테이블 | AI 템플릿 | 핵심 테스트 포인트 |
|---|---|---|---|---|---|---|
| FR-01 | 회원가입 및 인증 | SCR-01, SCR-02 | `/auth/oauth2/{provider}/authorize`, `/auth/oauth2/{provider}/callback`, `/auth/logout`, `/users/me` | `users`, `auth_accounts` | 없음 | OAuth 콜백, refresh 처리, 동일 이메일 다른 제공자 자동 병합 금지 |
| FR-02 | 포트폴리오 수집 및 저장 | SCR-03~SCR-07, SCR-17, SCR-18 | `/github/connections`, `/github/repositories`, `/github/repositories/selection`, `/github/repositories/{repositoryId}/sync-commits`, `/documents`, `/documents/{documentId}`, `/portfolios/me/readiness` | `github_connections`, `github_repositories`, `github_commits`, `documents` | `ai.portfolio.summary.v1` 선택 적용 | public/private 분기, scope 부족 처리, 10MB/5개 검증, 추출 실패 분리, 중복 업로드, 준비 현황 `null + not_ready` 표시, 전역 위젯과 상세 화면 동일 계약 사용 |
| FR-03 | 맞춤 자소서 생성 | SCR-08~SCR-10 | `/applications`, `/applications/{applicationId}`, `/applications/{applicationId}/sources`, `/applications/{applicationId}/questions`, `/applications/{applicationId}/questions/generate-answers` | `applications`, `application_source_repositories`, `application_source_documents`, `application_questions` | `ai.self_intro.generate.v1` | 직무 필수, source 저장, 문항 최대 10개, lengthOption, timeout 처리 |
| FR-04 | 면접 질문 생성 | SCR-11, SCR-12 | `/interview/question-sets`, `/interview/question-sets/{questionSetId}` | `interview_question_sets`, `interview_questions`, `applications` | `ai.interview.questions.generate.v1` | 질문 최대 20개, 질문 세트 편집, 3~20개 세션 진입 검증 |
| FR-05 | 텍스트 기반 모의 면접 | SCR-13 | `/interview/sessions`, `/interview/sessions/{sessionId}`, `/interview/sessions/{sessionId}/answers`, `/interview/sessions/{sessionId}/complete` | `interview_sessions`, `interview_answers`, `interview_questions` | `ai.interview.followup.generate.v1` | 활성 세션 1개 제한, 일반 답변 50~1000자, skip 분기, 30분 자동 pause, resume |
| FR-06 | 면접 결과 저장 및 히스토리 조회 | SCR-14~SCR-16 | `/interview/sessions`, `/interview/sessions/{sessionId}`, `/interview/sessions/{sessionId}/result` | `interview_sessions`, `interview_answers`, `feedback_tags`, `interview_answer_tags` | `ai.interview.evaluate.v1`, `ai.interview.summary.v1` | 결과 생성 실패 후 결과 재확인, 점수/태그 저장, 활성 세션 배너, 히스토리 상세는 detail/result 재사용 |

## 사용 규칙

- 새 기능을 추가할 때는 이 표에 행을 먼저 추가한다.
- 화면, API, DB, AI 템플릿 중 하나라도 비어 있으면 구현 전에 원본 문서를 먼저 보강한다.
- 테스트는 항상 정상 흐름과 실패 흐름을 함께 기록한다.
