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

/**
 * 업로드된 문서 파일에서 텍스트를 추출하는 컴포넌트.
 *
 * <p>지원 형식:
 * <ul>
 *   <li>PDF: Apache PDFBox</li>
 *   <li>DOCX: Apache POI</li>
 *   <li>Markdown: plain text 읽기 (UTF-8)</li>
 * </ul>
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

    public DocumentTextExtractor(@Value("${app.storage.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
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
        // storagePath = "uploads/uuid_file.pdf" → 파일명만 추출해 uploadDir 기준으로 절대 경로 구성
        String filename = Path.of(storagePath).getFileName().toString();
        Path absolutePath = uploadDir.resolve(filename);

        log.debug("Extracting text from: {} ({})", absolutePath, mimeType);

        String text = switch (mimeType) {
            case "application/pdf" -> extractPdf(absolutePath);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                -> extractDocx(absolutePath);
            case "text/markdown" -> extractMarkdown(absolutePath);
            default -> throw new ServiceException(
                ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "텍스트 추출을 지원하지 않는 형식입니다: " + mimeType
            );
        };

        // 추출 결과가 공백만 있거나 비어 있으면 실패로 간주 (스캔 PDF 등)
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
     * PDFBox를 사용해 PDF 파일에서 텍스트를 추출한다.
     *
     * <p>스캔 PDF는 텍스트 레이어가 없어 빈 문자열이 반환될 수 있다.
     * 호출자가 빈 결과를 DOCUMENT_EXTRACT_EMPTY로 처리한다.</p>
     */
    private String extractPdf(Path path) {
        try (PDDocument doc = Loader.loadPDF(path.toFile())) {
            return new PDFTextStripper().getText(doc);
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
     *
     * <p>IOEXception 외에도 손상된 파일에서 발생하는
     * NotOfficeXmlFileException(RuntimeException 계열)까지 모두 잡는다.</p>
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
     *
     * <p>Markdown은 plain text이므로 별도 파싱 없이 원문을 사용한다.</p>
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
