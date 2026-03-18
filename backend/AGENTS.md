# backend/AGENTS.md

이 디렉터리 또는 백엔드 구현 관련 작업에서는 아래 규칙을 추가로 따른다.

## 필수 확인

1. `docs/requirements.md`
2. `docs/project/open-items.md`
3. `openapi.yaml`
4. `docs/backend-conventions.md`
5. `docs/error-policy.md`
6. `docs/testing.md`
7. `docs/security.md`
8. `docs/observability.md`

## 구현 규칙

- Controller는 입출력과 인증/인가 확인에 집중하고 비즈니스 로직은 Service로 이동한다.
- DTO, Entity, 외부 API 응답 객체를 분리한다.
- 소유권 검증이 필요한 API는 현재 사용자 기준으로 검사한다.
- 외부 연동 실패와 내부 도메인 오류를 구분한다.
- 응답은 공통 응답 형식과 오류 코드 체계를 유지한다.
- `docs/project/open-items.md`에서 닫힌 enum, 길이 제한, 점수 범위, 태그 정책을 코드와 검증 규칙에 그대로 반영한다.

## 금지

- `RuntimeException` 하나로 예외를 뭉개는 구현
- `Map<String, Object>` 임시 응답
- 테스트 없이 Service 추가
- 문서 변경 없이 API/DB 구조 변경
- deprecated 경로 문서를 구현 원본으로 읽는 작업
