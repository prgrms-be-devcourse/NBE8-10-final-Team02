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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentTextExtractor 단위 테스트.
 *
 * <p>Spring context 없이 실행되며, 각 형식별 추출 정상 경로와
 * 예외 경로(손상 파일, 빈 결과)를 검증한다.</p>
 *
 * <p>테스트 파일은 각 테스트에서 @TempDir 내에 프로그래밍 방식으로 생성한다.</p>
 */
class DocumentTextExtractorTest {

    @TempDir
    Path tempDir;

    private DocumentTextExtractor extractor;

    @BeforeEach
    void setUp() {
        // tempDir를 uploadDir로 사용하는 extractor 생성
        extractor = new DocumentTextExtractor(tempDir.toString());
    }

    // =========================================================
    // PDF
    // =========================================================

    @Test
    void extractPdf_returnsText() throws IOException {
        // PDFBox로 텍스트가 포함된 PDF 생성
        String expected = "Hello PDF";
        Path pdfPath = createPdf(expected);

        // storagePath는 "상위디렉토리명/파일명" 형식 — getFileName()으로 파일명만 추출해 resolve
        String storagePath = "uploads/" + pdfPath.getFileName();
        String result = extractor.extract(storagePath, "application/pdf");

        assertThat(result).contains(expected);
    }

    @Test
    void extractPdf_throwsWhenCorrupted() throws IOException {
        // 손상된 파일(랜덤 바이트)을 .pdf 확장자로 저장
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
    // 빈 결과 → DOCUMENT_EXTRACT_EMPTY
    // =========================================================

    @Test
    void extract_throwsWhenResultBlank() throws IOException {
        // 텍스트가 없는 PDF (빈 페이지만)
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
    // Test helpers
    // =========================================================

    /** PDFBox로 텍스트가 포함된 PDF 파일을 tempDir에 생성한다. */
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

    /** 텍스트가 없는 빈 페이지 PDF를 tempDir에 생성한다. */
    private Path createEmptyPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            Path path = tempDir.resolve("empty.pdf");
            doc.save(path.toFile());
            return path;
        }
    }

    /** Apache POI로 텍스트가 포함된 DOCX 파일을 tempDir에 생성한다. */
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
