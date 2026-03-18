---
owner: AI 기능/생성 파이프라인
reviewer: 팀 전체
status: draft
last_updated: 2026-03-17
linked_issue_or_pr: -
applies_to: adr
---

# ADR-002: Use application as the center of generation flow

- 상태: Accepted
- 날짜: 2026-03-17

## 배경

자소서 생성과 면접 질문 생성 모두 회사명, 직무, 선택한 포트폴리오 소스, 문항 정보를 함께 필요로 한다.

## 결정

지원 준비 단위를 `application`으로 통일하고,
자소서 생성과 면접 질문 생성을 모두 이 엔티티 기준으로 연결한다.

## 이유

- 입력 축을 하나로 통일할 수 있다.
- 회사/직무별 여러 준비 흐름을 사용자별로 관리하기 쉽다.
- 자소서와 면접 질문의 연결성을 유지할 수 있다.
- 히스토리와 재생성 구조를 설계하기 쉽다.

## 결과

- `application_source_repositories`
- `application_source_documents`
- `application_questions`

위 구조를 통해 하나의 준비 단위에 필요한 입력을 묶는다.
