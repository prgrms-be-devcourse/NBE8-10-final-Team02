# Document Domain Changes Log

이 문서는 Document 도메인 개선 작업의 변경 이력과 설계 결정을 기록한다.

---

## Feature 1: PDF OCR 지원 (`feat/document-pdf-ocr`)

**목적**: 스캔 PDF(텍스트 레이어 없음) 업로드 시 `DOCUMENT_EXTRACT_EMPTY`로 실패하던 문제를 OCR로 해결.

### 변경 내용

| 파일 | 변경 유형 | 설명 |
|------|----------|------|
| `build.gradle.kts` | 의존성 추가 | `net.sourceforge.tess4j:tess4j:5.13.0` — Tesseract Java 바인딩 |
| `application.yml` | 설정 추가 | `app.ocr.*` 설정 블록 (enabled/tessdata-path/language) |
| `OcrService.java` | 신규 생성 | OCR 서비스 인터페이스 — 단위 테스트 Mock 주입 지원 |
| `TesseractOcrService.java` | 신규 생성 | Tesseract 실제 구현 (`app.ocr.enabled=true` 시 활성화) |
| `NoOpOcrService.java` | 신규 생성 | OCR 비활성화 시 No-Op 구현 (기본값, Tesseract 미설치 환경) |
| `DocumentTextExtractor.java` | 수정 | `OcrService` 주입, `extractPdf()` OCR 폴백 로직 추가 |
| `Dockerfile` | 수정 | `tesseract tesseract-langpack-eng tesseract-langpack-kor` 패키지 추가 |

### PDF 추출 흐름 (변경 후)

```
PDF 업로드
  └→ PDFBox 텍스트 추출
       ├→ [텍스트 있음] 반환 ✓
       └→ [텍스트 없음 = 스캔 PDF]
            └→ OcrService.extractTextFromPdf()
                 ├→ TesseractOcrService (app.ocr.enabled=true)
                 │    └→ PDFRenderer로 페이지 이미지화 → Tesseract OCR → 텍스트 반환
                 └→ NoOpOcrService (기본값)
                      └→ "" 반환 → DOCUMENT_EXTRACT_EMPTY 예외
```

### 설계 결정

- **인터페이스 분리**: `OcrService` 인터페이스를 만들어 단위 테스트에서 Tesseract 네이티브 라이브러리 없이 Mock 주입 가능.
- **ConditionalOnProperty**: Tesseract 없는 환경에서도 애플리케이션이 정상 기동되도록 `NoOpOcrService` fallback 제공.
- **스레드 안전성**: Tesseract 인스턴스는 스레드 안전하지 않으므로 호출마다 새 인스턴스 생성 (`createTesseract()`).
- **OCR 실패 처리**: OCR이 `ServiceException`을 던지면 빈 문자열로 처리해 `DOCUMENT_EXTRACT_EMPTY`로 귀결.
  - 이유: OCR 실패 = "텍스트를 추출할 수 없음"이므로 `DOCUMENT_EXTRACT_FAILED`보다 `DOCUMENT_EXTRACT_EMPTY`가 더 정확한 표현.

### 환경 설정 방법

**로컬 개발 (Tesseract 미설치)**:
```yaml
# application.yml 기본값
app.ocr.enabled: false  # NoOpOcrService 사용
```

**프로덕션 (Docker)**:
```bash
# .env 또는 환경변수
APP_OCR_ENABLED=true
APP_OCR_TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata
APP_OCR_LANGUAGE=eng+kor
```

Dockerfile에 이미 `tesseract tesseract-langpack-eng tesseract-langpack-kor` 패키지가 추가되어 있다.

### 테스트

- `OcrServiceTest`: `ITesseract` Mock으로 `TesseractOcrService` 동작 검증 (3개)
- `DocumentTextExtractorTest`: OCR 폴백 경로 검증 (8개, 기존 5개 + OCR 관련 3개 신규)
  - PDFBox가 텍스트를 찾으면 OCR 미호출 확인
  - 스캔 PDF → OCR 성공 경로
  - OCR도 빈 결과 → `DOCUMENT_EXTRACT_EMPTY`
  - OCR 예외 → `DOCUMENT_EXTRACT_EMPTY`

---

## Feature 2: 업로드 처리 시간 단축 (`perf/document-upload-bottleneck`)

> 작업 예정

---

## Feature 3: PGroonga 전문 검색 (`feat/document-pgroonga-search`)

> 작업 예정
