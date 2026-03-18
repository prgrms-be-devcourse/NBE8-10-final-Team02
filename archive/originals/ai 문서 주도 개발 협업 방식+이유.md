결론부터 말하면, 문서 기반으로 Codex와 협업할 때는 모든 규칙을 길게 늘어놓는 방식보다, 루트 `AGENTS.md` 하나에 작업 원칙을 압축하고 나머지는 역할별 문서로 분리하는 구조가 가장 현실적입니다. OpenAI는 Codex가 `AGENTS.md`를 작업 전에 자동으로 읽고, 더 가까운 디렉터리의 `AGENTS.md`가 더 일반적인 지침보다 우선한다고 설명합니다. 또 `AGENTS.md`에는 실행 방법, 빌드·테스트·린트 명령, 엔지니어링 규칙, 금지사항, 완료 기준과 검증 방법을 담고, 파일이 커지면 세부 규칙은 별도 문서로 빼는 방식을 권장합니다. ([OpenAI Developers][1])

실무 기준으로는 문서만으로 운영하지 않습니다. 문서는 판단 기준을 남기고, 실제 강제는 브랜치 보호, 상태 체크, CODEOWNERS, PR 템플릿, 린터와 CI가 맡는 구조가 보통 더 안전합니다. OpenAI도 Codex 커스터마이징 문서에서 `AGENTS.md`와 함께 pre-commit hook, linter 같은 강제 장치를 두라고 안내하고 있고, GitHub는 protected branch에서 승인 리뷰와 status check 통과를 요구할 수 있으며, CODEOWNERS 리뷰 요구도 연결할 수 있습니다. ([OpenAI Developers][2])

그래서 백엔드 4인 팀이 실제 서비스까지 생각한다면, 문서 구조는 아래처럼 잡는 게 좋습니다.

가장 최소로 필요한 문서

* `README.md`

  * 서비스 한 줄 소개
  * 로컬 실행 방법
  * 필수 환경 변수 목록
  * 주요 디렉터리 구조
  * 자주 쓰는 명령어
  * 배포 환경 개요
  * 신규 팀원이 처음 보는 문서

* `AGENTS.md`

  * Codex가 반드시 따라야 하는 작업 규칙
  * 예: 어떤 명령으로 테스트할지, 어떤 파일은 건드리면 안 되는지, DTO/Entity 규칙, PR 전 체크리스트
  * 너무 길게 쓰지 말고 핵심만 씁니다
  * 세부 규칙은 다른 문서로 링크

* `CONTRIBUTING.md`

  * 브랜치 전략
  * 커밋 메시지 규칙
  * PR 크기 기준
  * 리뷰 규칙
  * 머지 기준
  * 이슈 생성 방식
  * 팀원이 아닌 AI도 사실상 이 파일을 참고 기준으로 삼게 됩니다

* `docs/product.md`

  * MVP 범위
  * 제외 범위
  * 핵심 유저 플로우
  * 우선순위
  * 성공 기준
  * 이 문서가 없으면 AI가 기능을 과하게 만들거나, 없는 요구사항을 추정하기 쉽습니다

* `docs/architecture.md`

  * 시스템 구조
  * 주요 모듈 책임
  * 레이어 경계
  * 외부 연동 지점
  * 인증 흐름
  * 배포 구조
  * "어디까지가 어느 모듈 책임인지"를 명확히 해주는 문서입니다

* `docs/backend-conventions.md`

  * 패키지 구조
  * 클래스 네이밍
  * DTO/Entity/Service 분리 기준
  * 트랜잭션 기준
  * 예외 처리 규칙
  * 로깅 규칙
  * 테스트 네이밍 규칙
  * AI가 가장 자주 어기는 부분을 막는 문서입니다

* `docs/api-guidelines.md`

  * URL 규칙
  * 요청/응답 포맷
  * 인증 헤더 규칙
  * 페이징/정렬/필터 규칙
  * 상태코드 사용 기준
  * 실제 계약 자체는 가능하면 `openapi.yaml` 같은 기계 판독 파일이 원본이 되고, 이 md는 규칙과 설명을 담당하는 편이 좋습니다

* `docs/deployment-runbook.md`

  * dev/staging/prod 차이
  * 배포 절차
  * 롤백 절차
  * 환경 변수 반영 방법
  * 장애 시 첫 대응 순서
  * 실서비스 목표라면 최소한 이 문서는 꼭 있어야 합니다

이 정도가 진짜 최소 세트입니다. 학생 팀프로젝트여도 실제 서비스 기준으로 가려면, 적어도 `README.md`, `AGENTS.md`, `CONTRIBUTING.md`, `product.md`, `architecture.md`, `backend-conventions.md`, `deployment-runbook.md`까지는 있어야 문서 기반 협업이 안정적으로 굴러갑니다.

그다음부터는 강하게 추천하는 문서입니다.

* `docs/error-codes.md`

  * 공통 에러 코드
  * 사용자 메시지/내부 로그 구분
  * Validation 실패 형식
  * 프론트와 협업할 때 특히 중요

* `docs/testing.md`

  * 단위 테스트, 통합 테스트, E2E 범위
  * 어떤 계층을 어디까지 검증할지
  * 테스트 데이터 전략
  * 필수 테스트 항목

* `docs/db-migration.md`

  * 스키마 변경 절차
  * migration 작성 규칙
  * 롤백 가능성
  * 운영 DB 변경 금지 규칙
  * 인덱스 검토 기준

* `docs/security.md`

  * 인증/인가 규칙
  * secret 관리 원칙
  * 로그에 남기면 안 되는 값
  * 관리자 기능 보호 기준
  * 외부 공개 API 보안 기준

* `docs/observability.md`

  * 어떤 로그를 남길지
  * 필수 메트릭
  * 알람 기준
  * traceId/requestId 규칙
  * 대시보드 기준

* `docs/domain/*.md`

  * 예: `docs/domain/member.md`, `docs/domain/auction.md`
  * 각 도메인의 상태 변화, 핵심 정책, 예외 케이스
  * 비즈니스 규칙이 복잡해지면 이 문서가 정말 중요합니다

* `docs/adr/ADR-001-...md`

  * 중요한 설계 결정을 남기는 ADR 문서
  * 예: JWT를 왜 선택했는지, Redis를 어디에 쓰는지, soft delete를 왜 채택했는지
  * 나중에 "왜 이렇게 만들었는지" 설명하는 비용을 많이 줄여줍니다

* `PLANS.md`

  * 긴 작업이나 복잡한 리팩터링을 할 때 사용하는 실행 계획 템플릿
  * OpenAI는 복잡하거나 모호한 작업에서는 먼저 계획을 세우도록 권장하고, 더 긴 작업에는 `PLANS.md` 같은 실행 계획 템플릿을 둘 수 있다고 안내합니다. ([OpenAI Developers][3])

실무 기준으로 추천하는 최종 구조는 대략 이런 느낌입니다.

```text
/
├─ README.md
├─ AGENTS.md
├─ CONTRIBUTING.md
├─ openapi.yaml
├─ .github/
│  ├─ pull_request_template.md
│  └─ ISSUE_TEMPLATE/
│     ├─ bug_report.md
│     └─ feature_request.md
├─ docs/
│  ├─ product.md
│  ├─ architecture.md
│  ├─ backend-conventions.md
│  ├─ api-guidelines.md
│  ├─ error-codes.md
│  ├─ testing.md
│  ├─ db-migration.md
│  ├─ security.md
│  ├─ observability.md
│  ├─ deployment-runbook.md
│  ├─ release.md
│  ├─ incidents.md
│  ├─ domain/
│  │  ├─ member.md
│  │  └─ auction.md
│  └─ adr/
│     ├─ ADR-001-auth-strategy.md
│     └─ ADR-002-db-migration-policy.md
└─ backend/
   └─ AGENTS.md
```

여기서 중요한 건 `AGENTS.md`를 모든 규칙 저장소로 쓰지 않는 것입니다. 가장 좋은 방식은 이렇습니다.

* 루트 `AGENTS.md`

  * Codex 작업 원칙의 요약본
  * 어디를 먼저 읽어야 하는지 안내
  * 테스트/린트/검증 명령
  * 금지사항
  * 완료 기준

* 세부 문서

  * 정책, 도메인, 운영 절차, 규칙의 본문

* 하위 디렉터리 `AGENTS.md`

  * 특정 모듈만의 추가 규칙
  * 예: `backend/api/AGENTS.md`, `infra/AGENTS.md`

OpenAI 문서상 Codex는 더 가까운 위치의 `AGENTS.md`를 우선 적용하고, GitHub 연동 리뷰에서도 변경 파일과 가장 가까운 `AGENTS.md`의 review guideline을 따릅니다. 그래서 모듈별 규칙이 분명한 팀이라면 하위 `AGENTS.md`가 꽤 유용합니다. ([OpenAI Developers][1])

문서만큼 중요한 비 md 파일도 있습니다. 이건 같이 두는 게 사실상 필수입니다.

* `openapi.yaml`

  * API 계약의 원본
* `CODEOWNERS`

  * 파일/디렉터리 책임자 지정
* `.github/pull_request_template.md`

  * PR 설명 강제
* `.github/ISSUE_TEMPLATE/*.md` 또는 issue form yml

  * 이슈 입력 표준화
* `.github/workflows/*.yml`

  * CI/CD
* `.editorconfig`, formatter, linter 설정 파일

  * 문체와 스타일 강제
* Dockerfile, compose, env 예시 파일

  * 실행 환경 표준화

GitHub는 기여 가이드 파일을 저장소 루트, `docs`, `.github` 등에 둘 수 있고, PR이나 이슈를 열 때 그 가이드에 연결해 보여줄 수 있습니다. PR 템플릿은 PR 본문에 자동으로 채워지고, 이슈 템플릿은 md 또는 폼 기반 yml로 표준화할 수 있습니다. 즉, md 문서는 "설명", 템플릿과 규칙 파일은 "입력 표준화", 브랜치 보호와 CI는 "강제"를 맡기는 구조가 가장 실무적입니다. ([GitHub Docs][4])

실제로 Codex와 잘 맞는 문서 작성 방식도 따로 있습니다.

* 각 문서는 1개의 주제만 담당
* 문서마다 맨 위에 적용 범위 명시

  * 예: "이 문서는 백엔드 API 레이어에만 적용"
* 반드시 해야 하는 것과 권장사항 구분

  * MUST / SHOULD 수준으로 구분하면 좋음
* 금지 예시를 같이 작성

  * "Entity를 Controller 응답으로 직접 반환 금지"
* 애매한 표현 금지

  * "적절히", "깔끔하게", "웬만하면" 같은 표현은 AI가 해석하기 어렵습니다
* 실제 경로와 실제 명령어를 넣기

  * `./gradlew test`
  * `./gradlew spotlessApply`
* 중복 금지

  * API 규칙을 `README.md`, `AGENTS.md`, `backend-conventions.md`에 세 번 반복하지 않기
* 원본 우선순위 명시

  * 예: API는 `openapi.yaml` 우선
  * DB 변경은 migration 파일 우선
  * CI 기준은 workflow 파일 우선

특히 `AGENTS.md`는 짧고 정확해야 합니다. OpenAI도 짧고 정확한 `AGENTS.md`가, 길고 추상적인 규칙 문서보다 낫다고 설명합니다. `AGENTS.md`에는 자주 반복되는 핵심 규칙만 두고, 나머지는 링크로 넘기는 방식이 제일 좋습니다. ([OpenAI Developers][3])

정리하면, 백엔드 4인 팀이 문서 기준으로 Codex와 안정적으로 작업하려면 추천 우선순위는 이렇습니다.

* 반드시

  * `README.md`
  * `AGENTS.md`
  * `CONTRIBUTING.md`
  * `docs/product.md`
  * `docs/architecture.md`
  * `docs/backend-conventions.md`
  * `docs/api-guidelines.md`
  * `docs/deployment-runbook.md`

* 강력 추천

  * `docs/error-codes.md`
  * `docs/testing.md`
  * `docs/db-migration.md`
  * `docs/security.md`
  * `docs/observability.md`
  * `docs/domain/*.md`
  * `docs/adr/*`

* 문서만큼 중요

  * `openapi.yaml`
  * `CODEOWNERS`
  * PR/Issue 템플릿
  * CI 설정
  * 린트/포맷 설정

이 기준이면 문서가 과하게 많지도 않고, AI가 헷갈리지 않으면서도 실제 서비스 운영 기준까지 커버할 수 있습니다.