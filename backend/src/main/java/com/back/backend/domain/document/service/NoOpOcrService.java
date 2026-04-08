package com.back.backend.domain.document.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * OCR 비활성화 시 사용되는 No-Op 구현체.
 *
 * <p>{@code app.ocr.enabled}가 {@code false}이거나 설정되지 않은 경우에 활성화된다.
 * 텍스트를 추출하지 않고 빈 문자열을 반환하므로,
 * {@link DocumentTextExtractor}가 {@code DOCUMENT_EXTRACT_EMPTY}를 던진다.</p>
 *
 * <p>로컬 개발 환경이나 Tesseract가 설치되지 않은 환경의 기본 동작이다.</p>
 */
@Service
@ConditionalOnProperty(name = "app.ocr.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpOcrService implements OcrService {

    @Override
    public String extractTextFromPdf(PDDocument document) {
        // OCR 비활성화 → 빈 문자열 반환 → DocumentTextExtractor가 DOCUMENT_EXTRACT_EMPTY 처리
        return "";
    }
}
