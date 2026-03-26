---
owner: 포트폴리오 수집/외부 연동 + 품질/테스트
reviewer: 팀 전체
status: reviewed
last_updated: 2026-03-26
linked_issue_or_pr: feat/portfolio-readiness-dashboard
applies_to: portfolio-domain
---

# Domain: Portfolio

## 목적
사용자의 GitHub 활동과 업로드 문서를 수집, 저장, 정규화한다.

## 핵심 엔티티
- `github_connections`
- `github_repositories`
- `github_commits`
- `documents`

## 핵심 규칙
- GitHub URL 입력은 public repository 범위만 지원한다.
- private repository는 GitHub OAuth 추가 동의와 적절한 scope가 있을 때만 접근한다.
- MVP 기본 수집 대상은 선택된 repository와 사용자 본인 commit이다.
- PR, Issue 수집은 후속 확장으로 둔다.
- 업로드 허용 형식은 PDF, DOCX, MD다.
- 파일당 최대 용량은 10MB이고 사용자당 기본 업로드 한도는 5개다.
- 업로드 성공과 텍스트 추출 성공은 별개 상태다.
- 스캔 PDF는 추출 실패 가능 항목으로 분리한다.
- 동일 문서 재업로드 시 덮어쓰기 확인이 필요하다.
- 준비 현황 요약은 read-only orchestration API로 제공하며, 상세 대시보드와 전역 위젯이 공용으로 사용한다.
- v1에서 아직 정확 집계하지 않는 값은 가짜 숫자 대신 `null + not_ready`로 반환한다.

## 주요 API
- `GET /github/connections`
- `POST /github/connections`
- `GET /github/repositories`
- `PUT /github/repositories/selection`
- `POST /github/repositories/{repositoryId}/sync-commits`
- `POST /documents`
- `GET /documents`
- `GET /documents/{documentId}`
- `GET /portfolios/me/readiness`

## 대표 오류
- `GITHUB_URL_INVALID`
- `GITHUB_REPOSITORY_FORBIDDEN`
- `GITHUB_SCOPE_INSUFFICIENT`
- `GITHUB_COMMIT_SYNC_FAILED`
- `DOCUMENT_INVALID_TYPE`
- `DOCUMENT_FILE_TOO_LARGE`
- `DOCUMENT_EXTRACT_FAILED`
