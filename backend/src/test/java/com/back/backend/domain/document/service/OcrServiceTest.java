package com.back.backend.domain.document.service;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TesseractOcrService 단위 테스트.
 *
 * <p>ITesseract(인터페이스)를 Mock으로 주입해 Tesseract 네이티브 라이브러리 없이 테스트한다.
 * 실제 OCR 동작은 별도 수동 통합 테스트에서 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    ITesseract tesseract;

    /**
     * Tesseract가 텍스트를 반환하면 OcrService도 같은 텍스트를 반환한다.
     */
    @Test
    void extractTextFromPdf_returnsTesseractResult() throws TesseractException, IOException {
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("OCR 결과 텍스트");

        TesseractOcrService service = new TesseractOcrService(tesseract);

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage()); // 단순 빈 페이지 — 실제 OCR 실행 안 함(mock)
            String result = service.extractTextFromPdf(doc);
            assertThat(result).contains("OCR 결과 텍스트");
        }
    }

    /**
     * Tesseract가 TesseractException을 던지면 ServiceException(DOCUMENT_EXTRACT_FAILED)으로 변환한다.
     */
    @Test
    void extractTextFromPdf_throwsServiceExceptionOnTesseractFailure() throws TesseractException {
        when(tesseract.doOCR(any(BufferedImage.class))).thenThrow(new TesseractException("Tesseract 오류"));

        TesseractOcrService service = new TesseractOcrService(tesseract);

        assertThatThrownBy(() -> {
            try (PDDocument doc = new PDDocument()) {
                doc.addPage(new PDPage());
                service.extractTextFromPdf(doc);
            }
        })
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_EXTRACT_FAILED));
    }

    /**
     * Tesseract가 빈 문자열을 반환하면 OcrService도 빈 문자열을 반환한다.
     * (호출자인 DocumentTextExtractor가 DOCUMENT_EXTRACT_EMPTY를 처리한다.)
     */
    @Test
    void extractTextFromPdf_returnsBlankWhenTesseractReturnsBlank() throws TesseractException, IOException {
        when(tesseract.doOCR(any(BufferedImage.class))).thenReturn("  ");

        TesseractOcrService service = new TesseractOcrService(tesseract);

        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            String result = service.extractTextFromPdf(doc);
            assertThat(result).isBlank();
        }
    }
}
