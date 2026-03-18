# AI 기술 면접 연습 플랫폼 DB 설계 정리

## 1. 문서 개요

- 버전: v0.1
- 기준 문서:
  - AI 기술 면접 연습 플랫폼 요구사항 명세서 v0.2
  - AI 기술 면접 연습 플랫폼 ERD 초안 v0.1
- 목적: ERD 초안을 실제 PostgreSQL 스키마로 내리기 전에 필수 컬럼, 선택 컬럼, 제약조건, 삭제 정책, 인덱스 기준을 정리한다.

## 2. 핵심 설계 결정

### 2.1 계정 구조

- `users`는 서비스 사용자 기준 테이블이다.
- `auth_accounts`는 OAuth2 로그인 연결 정보이다.
- `github_connections`는 GitHub 저장소/커밋 동기화용 연결 정보이다.
- 따라서 GitHub 로그인과 GitHub 연동은 개념적으로 분리한다.

### 2.2 지원 단위 중심 구조

- 자소서 생성과 면접 질문 생성은 `applications`를 중심으로 묶는다.
- 하나의 `application`은 회사명, 직무, 자소서 문항, 선택한 소스 문서를 함께 가진다.
- 면접 질문 세트와 실제 모의 면접 세션도 `application` 기준으로 추적한다.

### 2.3 MVP 범위 반영 원칙

- GitHub는 repository 목록과 사용자 본인 commit 수집까지만 반영한다.
- PR, issue, 비용 로그, 비동기 잡 로그, 운영 감사 로그는 제외한다.
- 면접 결과는 점수, 태그, 짧은 근거까지 저장한다.

## 3. 테이블별 필수/선택 컬럼

## 3.1 users

필수 컬럼
- `id`
- `display_name`
- `status`
- `created_at`
- `updated_at`

선택 컬럼
- `email`
- `profile_image_url`

비고
- 이메일은 OAuth 제공자 정책에 따라 없을 수 있으므로 nullable로 둔다.
- 이메일은 값이 있을 때만 중복을 허용하지 않는 partial unique index를 둔다.

## 3.2 auth_accounts

필수 컬럼
- `id`
- `user_id`
- `provider`
- `provider_user_id`
- `is_primary`
- `connected_at`

선택 컬럼
- `provider_email`
- `last_login_at`

비고
- `(provider, provider_user_id)`는 유일해야 한다.
- 사용자당 하나의 primary 계정만 허용한다.

## 3.3 github_connections

필수 컬럼
- `id`
- `user_id`
- `github_user_id`
- `github_login`
- `sync_status`
- `connected_at`

선택 컬럼
- `access_scope`
- `last_synced_at`

비고
- MVP 기준으로 사용자당 GitHub 연결은 1개만 허용한다.
- `github_user_id`는 유일해야 한다.

## 3.4 github_repositories

필수 컬럼
- `id`
- `github_connection_id`
- `github_repo_id`
- `owner_login`
- `repo_name`
- `full_name`
- `html_url`
- `visibility`
- `is_selected`
- `synced_at`

선택 컬럼
- `default_branch`

비고
- `(github_connection_id, github_repo_id)`는 유일해야 한다.

## 3.5 github_commits

필수 컬럼
- `id`
- `repository_id`
- `github_commit_sha`
- `commit_message`
- `is_user_commit`
- `committed_at`

선택 컬럼
- `author_login`
- `author_name`
- `author_email`

비고
- `(repository_id, github_commit_sha)`는 유일해야 한다.
- `is_user_commit = true`인 데이터만 서비스 기능의 기본 입력으로 사용한다.

## 3.6 documents

필수 컬럼
- `id`
- `user_id`
- `document_type`
- `original_file_name`
- `storage_path`
- `mime_type`
- `file_size_bytes`
- `extract_status`
- `uploaded_at`

선택 컬럼
- `extracted_text`
- `extracted_at`

비고
- PDF, DOCX, MD를 우선 지원 대상으로 본다.
- 추출 실패도 저장해야 하므로 `extract_status`를 별도 관리한다.

## 3.7 applications

필수 컬럼
- `id`
- `user_id`
- `job_role`
- `status`
- `created_at`
- `updated_at`

선택 컬럼
- `application_title`
- `company_name`

비고
- 직무는 필수 입력이다.
- 회사명은 없는 상태로도 초안 생성이 가능하므로 nullable로 둔다.

## 3.8 application_source_repositories

필수 컬럼
- `id`
- `application_id`
- `repository_id`
- `created_at`

비고
- `(application_id, repository_id)`는 유일해야 한다.

## 3.9 application_source_documents

필수 컬럼
- `id`
- `application_id`
- `document_id`
- `created_at`

비고
- `(application_id, document_id)`는 유일해야 한다.

## 3.10 application_questions

필수 컬럼
- `id`
- `application_id`
- `question_order`
- `question_text`
- `created_at`
- `updated_at`

선택 컬럼
- `generated_answer`
- `edited_answer`
- `tone_option`
- `length_option`
- `emphasis_point`

비고
- `(application_id, question_order)`는 유일해야 한다.

## 3.11 interview_question_sets

필수 컬럼
- `id`
- `user_id`
- `application_id`
- `question_count`
- `difficulty_level`
- `created_at`

선택 컬럼
- `title`
- `question_types`

비고
- `question_types`는 PostgreSQL `text[]`로 저장한다.

## 3.12 interview_questions

필수 컬럼
- `id`
- `question_set_id`
- `question_order`
- `question_type`
- `difficulty_level`
- `question_text`
- `created_at`

선택 컬럼
- `parent_question_id`
- `source_application_question_id`

비고
- `(question_set_id, question_order)`는 유일해야 한다.
- 꼬리질문은 `parent_question_id`로 표현한다.

## 3.13 interview_sessions

필수 컬럼
- `id`
- `user_id`
- `question_set_id`
- `status`

선택 컬럼
- `total_score`
- `summary_feedback`
- `started_at`
- `ended_at`

비고
- `total_score`는 0~100 범위를 권장한다.

## 3.14 interview_answers

필수 컬럼
- `id`
- `session_id`
- `question_id`
- `answer_order`
- `is_skipped`
- `created_at`

선택 컬럼
- `answer_text`
- `score`
- `evaluation_rationale`

비고
- `(session_id, question_id)`는 유일해야 한다.
- `is_skipped = false`이면 `answer_text`가 있어야 한다.

## 3.15 feedback_tags

필수 컬럼
- `id`
- `tag_name`
- `tag_category`
- `created_at`

선택 컬럼
- `description`

비고
- `tag_name`은 유일해야 한다.

## 3.16 interview_answer_tags

필수 컬럼
- `id`
- `answer_id`
- `tag_id`
- `created_at`

비고
- `(answer_id, tag_id)`는 유일해야 한다.

## 4. enum 후보

- `user_status`: `active`, `withdrawn`
- `auth_provider`: `github`, `google`, `kakao`
- `github_sync_status`: `pending`, `success`, `failed`
- `repository_visibility`: `public`, `private`, `internal`
- `document_type`: `resume`, `award`, `certificate`, `other`
- `extract_status`: `pending`, `success`, `failed`
- `application_status`: `draft`, `completed`
- `difficulty_level`: `easy`, `medium`, `hard`
- `interview_question_type`: `experience`, `project`, `technical_cs`, `technical_stack`, `behavioral`, `follow_up`
- `interview_session_status`: `created`, `in_progress`, `completed`, `abandoned`
- `feedback_tag_category`: `content`, `structure`, `evidence`, `communication`, `technical`, `other`

## 5. 삭제 정책

- `users` 삭제 시 하위 데이터는 전체 삭제한다.
- `applications` 삭제 시 자소서 문항, 선택 소스 연결, 질문 세트는 함께 삭제한다.
- `interview_sessions` 삭제 시 답변과 답변 태그는 함께 삭제한다.
- `documents`, `github_repositories`, `feedback_tags`는 참조 중이면 삭제를 막는 방향을 기본값으로 둔다.

## 6. 인덱스 전략

필수 unique
- `auth_accounts(provider, provider_user_id)`
- `github_connections(user_id)`
- `github_connections(github_user_id)`
- `github_repositories(github_connection_id, github_repo_id)`
- `github_commits(repository_id, github_commit_sha)`
- `application_source_repositories(application_id, repository_id)`
- `application_source_documents(application_id, document_id)`
- `application_questions(application_id, question_order)`
- `interview_questions(question_set_id, question_order)`
- `interview_answers(session_id, question_id)`
- `interview_answer_tags(answer_id, tag_id)`

조회 최적화용 index
- `users(status)`
- `auth_accounts(user_id)`
- `github_repositories(github_connection_id, is_selected)`
- `github_commits(repository_id, committed_at desc)`
- `documents(user_id, document_type, uploaded_at desc)`
- `applications(user_id, created_at desc)`
- `interview_sessions(user_id, status, started_at desc)`
- `interview_answers(session_id, answer_order)`

## 7. 아직 열어둘 항목

- GitHub private repository를 언제 지원할지
- `applications.status`를 더 세분화할지
- 면접 세션 점수 산정 공식을 별도 테이블로 분리할지
- 태그 마스터를 코드성 데이터로 둘지 운영자가 관리하는 테이블로 둘지
- 자소서 생성 이력 버전 관리를 별도 테이블로 둘지
