# AGENTS.md

이 저장소는 문서를 기준으로 AI와 사람이 함께 개발하는 것을 전제로 한다.

## 먼저 읽을 문서

1. `docs/requirements.md`
2. `docs/project/open-items.md`
3. `openapi.yaml`
4. `docs/api-guidelines.md`
5. `docs/db/schema.md`
6. `docs/error-policy.md`
7. `docs/backend-conventions.md`
8. 작업에 따라 `docs/frontend/screen-spec.md` 또는 `docs/ai/prompt-templates.md`
9. 더 가까운 디렉터리의 `AGENTS.md`

## 핵심 규칙

- 문서에 없는 기능, 상태값, enum, 오류 코드, 응답 필드를 임의로 추가하지 않는다.
- 이미 `docs/project/open-items.md`에서 닫힌 결정은 다시 미결정처럼 다루지 않는다.
- 문서가 바뀌면 코드를 고치기 전에 문서를 먼저 맞춘다.
- `openapi.yaml`이 있으면 API 계약은 md 문서보다 `openapi.yaml`을 우선한다.
- migration 파일이 생기면 DB 문서보다 migration을 우선한다.
- AGENTS는 요약 규칙만 둔다. 세부 기준은 각 canonical 문서를 따른다.
- `docs/`는 팀 공통 문서 위치로 사용한다. 개인 구현 중 생기는 메모, 초안, 실험 문서는 root `.local/docs/`에 작성하고 Git 추적 대상으로 올리지 않는다.
- 개인 구현 문서를 공통 문서로 승격할 때는 필요한 canonical 문서에 반영하고, `.local/docs/`의 초안을 규칙 원본처럼 직접 참조하지 않는다.
- 같은 변경은 같은 PR에서 닫는다. API, DB, 오류 코드, 화면 상태, AI 출력 스키마, 테스트가 바뀌면 관련 문서를 같이 수정한다.
- Entity를 응답으로 직접 노출하지 않는다.
- 외부 API 호출을 긴 트랜잭션 안에 넣지 않는다.
- MVP 밖 기능은 문서 선반영 없이 구현하지 않는다.
- `archive/originals/`와 `status: deprecated` 문서는 참조 비교용으로만 보고 새 구현의 직접 원본으로 사용하지 않는다.

## 작업 중단 조건

- 상위 문서가 비어 있거나 서로 충돌하는 경우
- `docs/project/open-items.md`와 canonical 문서가 서로 다른 값을 가지는 경우
- 미결정 항목을 사실처럼 확정해야 하는 경우
- 새 enum, 상태값, 오류 코드를 추가해야 하는 경우
- 화면, API, DB, AI 출력 스키마 중 하나라도 연결이 비어 있는 경우

## 완료 전 확인

- 관련 테스트를 추가했는가
- 관련 문서를 같은 변경에 반영했는가
- 새 enum, 상태값, 오류 코드를 임의로 만들지 않았는가
- 민감정보가 로그에 남지 않는가
- canonical 문서가 deprecated 경로를 다시 참조하지 않는가

## 실행 명령 관련 주의

현재 이 세트는 문서 중심 시작점이다.
실제 코드 저장소의 빌드, 테스트, 린트 명령이 확정되기 전에는 AI가 임의의 실행 명령을 사실처럼 단정하지 않는다.
