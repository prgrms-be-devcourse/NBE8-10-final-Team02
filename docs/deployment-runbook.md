---
owner: 플랫폼/공통 기반 + 인프라/배포/관측성
reviewer: 팀 전체
status: draft
last_updated: 2026-03-17
linked_issue_or_pr: -
applies_to: deployment-and-rollback
---

# Deployment Runbook

## 1. 목적

배포와 롤백, 운영 점검의 최소 절차를 남기는 초기 runbook이다.

## 2. 환경 구분

- local
- dev
- prod

환경별로 아래를 분리한다.

- DB
- secret
- 외부 API key
- 스토리지 설정
- OAuth callback URL

## 3. 배포 전 점검

- 필수 환경 변수 존재 여부
- DB migration 준비 여부
- health check endpoint 확인
- 외부 연동 key 유효성 확인
- 문서 변경 동기화 확인

## 4. 배포 순서 초안

1. 변경 문서 확인
2. migration 적용 준비
3. 애플리케이션 배포
4. health check 확인
5. 핵심 시나리오 smoke test
   - 로그인
   - GitHub 연결
   - 문서 업로드
   - 자소서 생성
   - 질문 생성
   - 세션 시작/결과 조회

## 5. 롤백 기준

아래 중 하나면 롤백 검토

- 로그인 불가
- 공통 인증 실패 급증
- 핵심 API 5xx 급증
- migration 실패
- 주요 외부 연동 전면 장애

## 6. 배포 후 확인

- application health
- DB 연결
- 외부 API 호출 성공 여부
- requestId 기반 오류 로그 확인

## 7. 미결정 항목

- 실제 배포 플랫폼
- 실제 smoke test 명령
- DB 백업/복원 절차 수치
- 운영 알람 임계치
