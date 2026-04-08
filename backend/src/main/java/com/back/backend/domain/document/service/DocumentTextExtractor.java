package com.back.backend.domain.document.service;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * 업로드된 문서 파일에서 텍스트를 추출하는 컴포넌트.
 *
 * <p>지원 형식:
 * <ul>
 *   <li>PDF: Apache PDFBox (텍스트 레이어 우선) → OCR 폴백 ({@link OcrService})</li>
 *   <li>DOCX: Apache POI</li>
 *   <li>Markdown: plain text 읽기 (UTF-8)</li>
 * </ul>
 * </p>
 *
 * <p>PDF 처리 순서:
 * <ol>
 *   <li>PDFBox로 텍스트 레이어 추출 시도 — 대부분의 일반 PDF 처리</li>
 *   <li>결과가 비어 있으면 {@link OcrService}로 OCR 폴백 — 스캔 PDF 대응</li>
 *   <li>OCR도 실패하거나 빈 결과면 {@code DOCUMENT_EXTRACT_EMPTY} 예외</li>
 * </ol>
 * </p>
 *
 * <p>storagePath는 DB에 저장된 상대 경로 (예: "uploads/uuid_resume.pdf").
 * 이 클래스는 uploadDir 기준으로 파일명 부분만 추출해 절대 경로로 변환한다.</p>
 */
@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);

    /** 파일이 저장된 업로드 디렉토리 (절대 경로). */
    private final Path uploadDir;

    /** OCR 서비스 — Tesseract 활성화 시 TesseractOcrService, 비활성화 시 NoOpOcrService. */
    private final Optional<OcrService> ocrService;

    public DocumentTextExtractor(
            @Value("${app.storage.upload-dir}") String uploadDir,
            Optional<OcrService> ocrService) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.ocrService = ocrService;
    }

    /**
     * 주어진 storagePath의 파일에서 텍스트를 추출해 반환한다.
     *
     * @param storagePath DB에 저장된 상대 경로 (예: "uploads/uuid_file.pdf")
     * @param mimeType    파일 MIME type — 추출 방식 결정에 사용
     * @return 추출된 텍스트 (비어 있으면 예외 발생)
     * @throws ServiceException 추출 실패 시 DOCUMENT_EXTRACT_FAILED,
     *                          추출 결과가 비어 있으면 DOCUMENT_EXTRACT_EMPTY
     */
    public String extract(String storagePath, String mimeType) {
        String filename = Path.of(storagePath).getFileName().toString();
        Path absolutePath = uploadDir.resolve(filename);

        log.debug("Extracting text from: {} ({})", absolutePath, mimeType);

        String text = switch (resolveFormat(filename, mimeType)) {
            case "pdf" -> extractPdf(absolutePath);
            case "docx" -> extractDocx(absolutePath);
            case "md" -> extractMarkdown(absolutePath);
            default -> throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "텍스트 추출을 지원하지 않는 형식입니다: " + mimeType
            );
        };

        if (text == null || text.isBlank()) {
            throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_EMPTY,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "문서에서 텍스트를 추출할 수 없습니다. 스캔 PDF이거나 내용이 없습니다."
            );
        }
        return text.strip();
    }

    /**
     * MIME type과 파일명 확장자를 조합해 추출 형식("pdf"/"docx"/"md"/unknown)을 결정한다.
     */
    private static String resolveFormat(String filename, String mimeType) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".docx")) return "docx";
        if (lower.endsWith(".md")) return "md";
        return switch (mimeType) {
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "text/markdown", "text/x-markdown", "text/plain" -> "md";
            default -> "unknown";
        };
    }

    /**
     * PDF에서 텍스트를 추출한다.
     *
     * <ol>
     *   <li>PDFBox의 {@link PDFTextStripper}로 텍스트 레이어 추출 시도</li>
     *   <li>결과가 비어 있으면 {@link OcrService}로 OCR 폴백 (스캔 PDF 대응)</li>
     * </ol>
     *
     * <p>OCR이 비활성화된 경우({@link NoOpOcrService}) 스캔 PDF는 빈 문자열을 반환하며,
     * 호출자({@link #extract})가 {@code DOCUMENT_EXTRACT_EMPTY}를 던진다.</p>
     */
    private String extractPdf(Path path) {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            if (!text.isBlank()) {
                return text;
            }
            // 텍스트 레이어가 없음 → OCR 시도 (스캔 PDF)
            log.info("PDF has no text layer, attempting OCR: {}", path.getFileName());
            if (ocrService.isEmpty()) {
                // OCR 서비스가 없으면 빈 문자열 반환
                log.info("OCR service not available, returning empty string");
                return "";
            }
            try {
                return ocrService.get().extractTextFromPdf(doc);
            } catch (ServiceException ocrEx) {
                // OCR 실패 → 빈 문자열 반환 → 호출자가 DOCUMENT_EXTRACT_EMPTY 처리
                log.warn("OCR failed for {}: {}", path.getFileName(), ocrEx.getMessage());
                return "";
            }
        } catch (IOException e) {
            log.warn("PDF extraction failed for {}: {}", path.getFileName(), e.getMessage());
            throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "PDF 텍스트 추출에 실패했습니다."
            );
        }
    }

    /**
     * Apache POI를 사용해 DOCX 파일에서 텍스트를 추출한다.
     */
    private String extractDocx(Path path) {
        try (InputStream is = Files.newInputStream(path);
             XWPFDocument doc = new XWPFDocument(is);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            log.warn("DOCX extraction failed for {}: {}", path.getFileName(), e.getMessage());
            throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "DOCX 텍스트 추출에 실패했습니다."
            );
        }
    }

    /**
     * Markdown 파일을 UTF-8로 읽어 그대로 반환한다.
     */
    private String extractMarkdown(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Markdown extraction failed for {}: {}", path.getFileName(), e.getMessage());
            throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Markdown 텍스트 읽기에 실패했습니다."
            );
        }
    }
}
