package com.back.backend.domain.document.service;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DocumentTextExtractor 단위 테스트.
 *
 * <p>Spring context 없이 실행되며, 각 형식별 추출 정상 경로와
 * 예외 경로(손상 파일, 빈 결과)를 검증한다.</p>
 *
 * <p>OcrService는 Mockito로 주입해 Tesseract 네이티브 라이브러리 없이 테스트한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    @Mock
    OcrService ocrService;

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new DocumentTextExtractor(tempDir.toString(), Optional.of(ocrService));
    }

    // =========================================================
    // PDF — PDFBox 정상 추출
    // =========================================================

    @Test
    void extractPdf_returnsText() throws IOException {
        String expected = "Hello PDF";
        Path pdfPath = createPdf(expected);

        String storagePath = "uploads/" + pdfPath.getFileName();
        String result = extractor.extract(storagePath, "application/pdf");

        assertThat(result).contains(expected);
        // PDFBox가 텍스트를 찾으면 OCR을 호출하지 않아야 한다
        verify(ocrService, never()).extractTextFromPdf(any());
    }

    @Test
    void extractPdf_throwsWhenCorrupted() throws IOException {
        Path corruptPdf = tempDir.resolve("corrupt.pdf");
        Files.write(corruptPdf, new byte[]{0x00, 0x01, 0x02});

        assertThatThrownBy(() ->
            extractor.extract("uploads/corrupt.pdf", "application/pdf")
        )
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_EXTRACT_FAILED));
    }

    // =========================================================
    // PDF — OCR 폴백 (스캔 PDF 대응)
    // =========================================================

    @Test
    void extractPdf_fallsBackToOcr_whenPdfBoxReturnsBlank() throws IOException {
        // OCR 서비스는 텍스트를 반환한다
        when(ocrService.extractTextFromPdf(any(PDDocument.class))).thenReturn("스캔 PDF OCR 결과");

        Path emptyPdf = createEmptyPdf();
        String storagePath = "uploads/" + emptyPdf.getFileName();

        String result = extractor.extract(storagePath, "application/pdf");

        assertThat(result).contains("스캔 PDF OCR 결과");
        verify(ocrService).extractTextFromPdf(any(PDDocument.class));
    }

    @Test
    void extractPdf_throwsExtractEmpty_whenOcrAlsoReturnsBlank() throws IOException {
        // OCR 서비스도 빈 문자열을 반환 — 최종적으로 DOCUMENT_EXTRACT_EMPTY
        when(ocrService.extractTextFromPdf(any(PDDocument.class))).thenReturn("  ");

        Path emptyPdf = createEmptyPdf();
        String storagePath = "uploads/" + emptyPdf.getFileName();

        assertThatThrownBy(() ->
            extractor.extract(storagePath, "application/pdf")
        )
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_EXTRACT_EMPTY));
    }

    @Test
    void extractPdf_throwsExtractEmpty_whenOcrThrowsServiceException() throws IOException {
        // OCR 서비스가 DOCUMENT_EXTRACT_FAILED를 던지면 → 빈 결과로 처리 → DOCUMENT_EXTRACT_EMPTY
        when(ocrService.extractTextFromPdf(any(PDDocument.class)))
            .thenThrow(new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                "OCR 실패"
            ));

        Path emptyPdf = createEmptyPdf();
        String storagePath = "uploads/" + emptyPdf.getFileName();

        assertThatThrownBy(() ->
            extractor.extract(storagePath, "application/pdf")
        )
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_EXTRACT_EMPTY));
    }

    // =========================================================
    // DOCX
    // =========================================================

    @Test
    void extractDocx_returnsText() throws IOException {
        String expected = "Hello DOCX";
        Path docxPath = createDocx(expected);

        String storagePath = "uploads/" + docxPath.getFileName();
        String result = extractor.extract(storagePath,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertThat(result).contains(expected);
    }

    @Test
    void extractDocx_throwsWhenCorrupted() throws IOException {
        Path corruptDocx = tempDir.resolve("corrupt.docx");
        Files.write(corruptDocx, new byte[]{0x00, 0x01, 0x02});

        assertThatThrownBy(() ->
            extractor.extract("uploads/corrupt.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                .isEqualTo(ErrorCode.DOCUMENT_EXTRACT_FAILED));
    }

    // =========================================================
    // Markdown
    // =========================================================

    @Test
    void extractMarkdown_returnsText() throws IOException {
        String expected = "# My Resume\n\nHello Markdown";
        Path mdPath = tempDir.resolve("resume.md");
        Files.writeString(mdPath, expected);

        String result = extractor.extract("uploads/resume.md", "text/markdown");

        assertThat(result).contains("Hello Markdown");
    }

    // =========================================================
    // Test helpers
    // =========================================================

    private Path createPdf(String text) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            Path path = tempDir.resolve("test.pdf");
            doc.save(path.toFile());
            return path;
        }
    }

    private Path createEmptyPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            Path path = tempDir.resolve("empty.pdf");
            doc.save(path.toFile());
            return path;
        }
    }

    private Path createDocx(String text) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph para = doc.createParagraph();
            XWPFRun run = para.createRun();
            run.setText(text);
            Path path = tempDir.resolve("test.docx");
            try (OutputStream os = Files.newOutputStream(path)) {
                doc.write(os);
            }
            return path;
        }
    }
}
