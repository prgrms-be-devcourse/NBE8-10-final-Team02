# Document Domain Changes Log

이 문서는 Document 도메인 개선 작업의 변경 이력과 설계 결정을 기록한다.

---

## Feature 2: 업로드 처리 시간 단축 (`perf/document-upload-bottleneck`)

**목적**: 1KB 문서도 수 초가 걸리는 추출 처리 시간 단축. HTTP 업로드 응답은 빠르나 async 추출이 느림.

### 병목 분석

| 단계 | 추정 소요시간 | 원인 |
|------|-------------|------|
| PDFBox/POI 추출 | ~100ms | 파일 크기 비례 |
| TextSanitization | <5ms | 단순 정규식 |
| **Gitleaks subprocess** | **500ms~2s** | **Go 바이너리 기동 비용** ← 주요 병목 |
| PII 마스킹 | ~10ms | 단순 정규식 |

### 변경 내용

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `SecretMaskingService.java` | 수정 | 소형 문서 fast-path — threshold 이하 문서는 Gitleaks 건너뜀 |
| `DocumentExtractionService.java` | 수정 | 각 단계별 소요 시간 INFO 로그 (`[doc=N] stage=Xms` 형식) |
| `AsyncConfig.java` | 수정 | 스레드 풀 확장: 2/4/50 → 4/8/100 |
| `application.yml` | 수정 | `app.secret-masking.small-doc-skip-threshold-chars: 2000` 설정 추가 |

### 설계 결정

- **Threshold = 2000자** (≈ 약 1~2KB): 소형 문서는 프로덕션 API 키 포함 가능성이 낮아 Gitleaks 건너뜀. 필요 시 `APP_SECRET_MASKING_THRESHOLD=0`으로 항상 스캔.
- **타이밍 로그**: 단계별 ms 단위 로그로 실제 운영에서 병목 구간을 가시화.
- **스레드 풀 확장**: Gitleaks가 스레드를 블로킹하므로 코어를 늘려 다른 문서 동시 처리 개선.

### 기대 효과

- 2000자 이하 문서: Gitleaks 스킵 → 추출 시간 500ms~2s 단축
- 동시 업로드: 코어 스레드 증가로 큐잉 감소

### 테스트

- `SecretMaskingServiceTest`: threshold fast-path 동작 검증 (7개 신규)
  - threshold 이하 → Gitleaks 미호출
  - threshold 초과 → Gitleaks 호출
  - 시크릿 발견 시 [REDACTED] 치환

---

## Feature 1: PDF OCR 지원 (`feat/document-pdf-ocr`)

> 별도 브랜치 `feat/document-pdf-ocr` 참고.

---

## Feature 3: PGroonga 전문 검색 (`feat/document-pgroonga-search`)

> 작업 예정
