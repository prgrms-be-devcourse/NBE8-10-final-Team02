# Document Domain Changes Log

이 문서는 Document 도메인 개선 작업의 변경 이력과 설계 결정을 기록한다.

---

## Feature 3: PGroonga 전문 검색 (`feat/document-pgroonga-search`)

**목적**: 단순 LIKE 검색 대신 PGroonga `&@~` 연산자로 한국어/영어 FTS + Fuzzy search 지원.

### 변경 내용

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `docker-compose.yml` | 수정 | `postgres:15-alpine` → `groonga/pgroonga:latest-alpine-15` |
| `V19__add_pgroonga_search.sql` | 신규 | PGroonga 확장 생성 + FTS 인덱스 (fail-safe) |
| `DocumentRepository.java` | 수정 | `searchByText`, `searchByFileName` 네이티브 쿼리 추가 |
| `DocumentService.java` | 수정 | `search()` 메서드 추가 (텍스트 + 파일명 검색 합산) |
| `DocumentController.java` | 수정 | `GET /api/v1/documents/search?q=` 엔드포인트 추가 |
| `openapi.yaml` | 수정 | `/documents/search` 엔드포인트 명세 추가 |
| `PGroongaTestcontainersConfiguration.java` | 신규 | PGroonga Docker 이미지 사용 TC 설정 |
| `DocumentSearchRepositoryTest.java` | 신규 | PGroonga FTS 통합 테스트 (7개) |
| `DocumentApiTest.java` | 수정 | 검색 API 테스트 3개 추가 (19개 → 22개) |

### 설계 결정

- **fail-safe 마이그레이션**: V19 마이그레이션은 `DO $$ EXCEPTION`으로 감싸 PGroonga 미설치 환경에서도 Flyway가 통과한다. 다만 `&@~` 연산자는 PGroonga 없이는 쿼리 실패한다.
- **분리된 TC 설정**: 일반 통합 테스트(`postgres:16-alpine`)와 PGroonga 테스트(`groonga/pgroonga:latest-alpine-16`)를 TC 설정으로 분리해 기존 테스트에 영향 없음.
- **검색 합산**: `searchByText` + `searchByFileName` 결과를 LinkedHashMap으로 합산해 중복 제거. 텍스트 검색 결과가 우선.
- **결과 제한**: 최대 20개, `pgroonga_score` 내림차순 — 관련도 높은 문서가 먼저 노출.

### API 명세

```
GET /api/v1/documents/search?q={keyword}
Authorization: Cookie JWT 필요
Response: ApiResponse<List<DocumentResponse>>
```

### 환경 요구사항

**로컬 개발**: `docker-compose.yml`이 `groonga/pgroonga:latest-alpine-15`를 사용하므로 `docker-compose up db` 시 PGroonga 사용 가능.

**테스트**: `DocumentSearchRepositoryTest`는 `groonga/pgroonga:latest-alpine-16` TC 이미지를 자동으로 Pull해 사용.

### 테스트

- `DocumentSearchRepositoryTest`: PGroonga TC 이미지 기반 실제 FTS 동작 검증 (7개)
  - 키워드로 extractedText 검색
  - 결과 없음 처리
  - 다른 사용자 문서 접근 차단
  - PENDING 문서 검색 제외
  - 한국어 키워드 검색
  - 파일명 검색
- `DocumentApiTest`: 검색 API HTTP 레이어 검증 3개 추가

---

## Feature 2: 업로드 처리 시간 단축 (`perf/document-upload-bottleneck`)

> 별도 브랜치 `perf/document-upload-bottleneck` 참고.

---

## Feature 1: PDF OCR 지원 (`feat/document-pdf-ocr`)

> 별도 브랜치 `feat/document-pdf-ocr` 참고.
