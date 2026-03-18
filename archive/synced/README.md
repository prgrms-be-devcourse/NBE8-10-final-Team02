# synced archive

이 디렉터리는 `archive/originals/`의 초기 설계 문서를 현재 canonical 문서 기준으로 다시 맞춘 동기화 사본을 보관한다.

중요 원칙

- 실제 구현 기준 원본은 `docs/`와 `openapi.yaml`이다.
- 이 디렉터리는 비교, 설명, 회귀 확인용 참조 사본이다.
- `archive/originals/`의 파일은 그대로 유지했고, 새 문서는 모두 이 디렉터리에 추가했다.

## 매핑 표

| originals 기준 문서 | synced 사본 | 현재 canonical 원본 |
|---|---|---|
| 프로젝트 기획서 보강본 | `AI_기술_면접_연습_플랫폼_프로젝트_기획서_v1.0.md` | `docs/product.md`, `docs/project/wbs.md`, `docs/architecture.md` |
| 요구사항 명세서 | `AI_기술_면접_연습_플랫폼_요구사항_명세서_v1.0.md` | `docs/requirements.md` |
| 비기능 요구사항 | `AI_기술_면접_연습_플랫폼_비기능_요구사항_v1.0.md` | `docs/non-functional.md` |
| 예외 및 오류 처리 정책 | `AI_기술_면접_연습_플랫폼_예외_및_오류_처리_정책_v1.0.md` | `docs/error-policy.md` |
| 공통 개발 규칙 | `AI_기술_면접_연습_플랫폼_공통_개발_규칙_v1.0.md` | `docs/backend-conventions.md` |
| API 명세서 초안 | `AI_기술_면접_연습_플랫폼_API_명세서_v1.0.md` | `openapi.yaml`, `docs/api-guidelines.md` |
| DB 설계 정리 | `AI_기술_면접_연습_플랫폼_DB_설계_정리_v1.0.md` | `docs/db/schema.md` |
| ERD 초안 | `AI_기술_면접_연습_플랫폼_ERD_v1.0.md` | `docs/db/erd.md` |
| 화면 정의서 초안 | `AI_기술_면접_연습_플랫폼_화면_정의서_v1.0.md` | `docs/frontend/screen-spec.md` |
| 와이어프레임 초안 | `AI_기술_면접_연습_플랫폼_와이어프레임_v1.0.md` | `docs/frontend/wireframes.md` |
| AI 문서 주도 개발 협업 운영 가이드 | `AI_기술_면접_연습_플랫폼_AI_문서_주도_개발_협업_운영_가이드_v1.0.md` | `docs/ai-collaboration.md` |
| AI 프롬프트 템플릿 설계서 | `AI_기술_면접_연습_플랫폼_AI_프롬프트_템플릿_설계서_v1.0.md` | `docs/ai/prompt-templates.md` |
| WBS | `AI_기술_면접_연습_플랫폼_WBS_v1.0.md` | `docs/project/wbs.md` |
| AI 문서 주도 개발 협업 방식+이유 | `AI_문서_주도_개발_협업_방식과_이유_v1.0.md` | `docs/ai-collaboration.md`, `AGENTS.md` |

## 동기화 원칙

- 가능하면 canonical 문서를 그대로 반영했다.
- archive 용도로 제목/경로/설명만 보강했다.
- API 문서는 `openapi.yaml` 기준으로 요약 재작성했다.
- 프로젝트 기획서는 `docs/product.md`와 `docs/project/wbs.md`를 기준으로 재구성했다.
