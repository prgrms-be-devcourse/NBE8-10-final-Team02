package com.back.backend.domain.document.service;

import org.apache.pdfbox.pdmodel.PDDocument;

/**
 * PDF 문서에서 OCR(광학 문자 인식)로 텍스트를 추출하는 서비스 인터페이스.
 *
 * <p>구현체는 두 가지다:</p>
 * <ul>
 *   <li>{@link TesseractOcrService}: Tesseract 네이티브 라이브러리를 사용하는 실제 OCR 구현
 *       ({@code app.ocr.enabled=true}일 때 활성화)</li>
 *   <li>{@link NoOpOcrService}: OCR 비활성화 시 빈 문자열을 반환 (기본값)</li>
 * </ul>
 *
 * <p>이 인터페이스를 통해 단위 테스트에서 Tesseract 네이티브 라이브러리 없이 Mock 주입이 가능하다.</p>
 */
public interface OcrService {

    /**
     * 이미 열린 {@link PDDocument}에서 OCR로 텍스트를 추출한다.
     *
     * <p>문서의 각 페이지를 고해상도 이미지로 렌더링한 후 OCR을 수행한다.
     * 이 메서드는 {@code document}를 닫지 않는다 — 수명 관리는 호출자 책임이다.</p>
     *
     * @param document 텍스트를 추출할 PDF 문서 (이미 열려 있어야 함)
     * @return OCR로 추출된 텍스트 (페이지 구분 포함). 빈 문자열이면 호출자가 처리한다.
     */
    String extractTextFromPdf(PDDocument document);
}
