package com.back.backend.domain.document.service;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Tesseract OCR 엔진을 사용해 스캔 PDF에서 텍스트를 추출하는 서비스.
 *
 * <p>{@code app.ocr.enabled=true}일 때만 Spring 빈으로 등록된다.
 * Tesseract 네이티브 라이브러리와 tessdata가 런타임에 설치되어 있어야 한다.</p>
 *
 * <h3>스레드 안전성</h3>
 * <p>Tesseract 인스턴스는 스레드 안전하지 않으므로 호출마다 새 인스턴스를 생성한다.
 * 추출 작업은 비동기 thread pool({@code documentTaskExecutor})에서 실행되므로
 * 이 방식으로 동시성 문제를 방지한다.</p>
 *
 * <h3>환경 설정</h3>
 * <pre>
 * app:
 *   ocr:
 *     enabled: true
 *     tesseract-data-path: /usr/share/tesseract-ocr/5/tessdata
 *     language: eng+kor
 * </pre>
 *
 * <h3>Docker 설치</h3>
 * <p>Dockerfile의 microdnf install에 다음 패키지를 추가한다:</p>
 * <pre>
 * tesseract tesseract-langpack-eng tesseract-langpack-kor
 * </pre>
 */
@Service
@ConditionalOnProperty(name = "app.ocr.enabled", havingValue = "true", matchIfMissing = false)
public class TesseractOcrService implements OcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrService.class);

    /** PDF 페이지를 이미지로 렌더링할 해상도(DPI). 높을수록 정확도가 올라가나 속도가 느려진다. */
    private static final float RENDER_DPI = 300f;

    private final String tessdataPath;
    private final String language;

    /**
     * 테스트 전용 Mock Tesseract 인스턴스.
     * null이면 {@link #createTesseract()}로 실제 인스턴스를 생성한다.
     */
    private final ITesseract mockTesseract;

    /**
     * 프로덕션 환경에서 Spring이 주입하는 생성자.
     */
    public TesseractOcrService(
            @Value("${app.ocr.tesseract-data-path:}") String tessdataPath,
            @Value("${app.ocr.language:eng+kor}") String language) {
        this.tessdataPath = tessdataPath;
        this.language = language;
        this.mockTesseract = null;
        log.info("TesseractOcrService initialized. tessdata-path='{}', language='{}'", tessdataPath, language);
    }

    /**
     * 테스트 전용 생성자: {@link ITesseract} Mock을 주입해 네이티브 라이브러리 없이 단위 테스트한다.
     */
    TesseractOcrService(ITesseract tesseract) {
        this.tessdataPath = "";
        this.language = "eng+kor";
        this.mockTesseract = tesseract;
    }

    @Override
    public String extractTextFromPdf(PDDocument document) {
        ITesseract tess = (mockTesseract != null) ? mockTesseract : createTesseract();
        PDFRenderer renderer = new PDFRenderer(document);
        StringBuilder sb = new StringBuilder();

        int totalPages = document.getNumberOfPages();

        for (int i = 0; i < totalPages; i++) {
            BufferedImage image = null;
            try {
                image = renderer.renderImageWithDPI(i, RENDER_DPI, ImageType.GRAY);
                String pageText = tess.doOCR(image);
                if (pageText != null) {
                    sb.append(pageText);
                    if (i < totalPages - 1) sb.append("\n");
                }
            } catch (IOException | TesseractException e) {
                // 한 페이지 실패가 전체 OCR 결과를 버리지 않도록 처리한 페이지까지의 텍스트는 보존한다.
                // (이전 동작은 50번째 페이지 실패 시 1~49 페이지 결과를 모두 폐기했다.)
                log.warn("OCR 실패 (페이지 {}/{}): {} — 처리한 페이지까지의 결과만 반환",
                    i, totalPages, e.getMessage());
                if (sb.length() == 0) {
                    // 첫 페이지부터 실패한 경우에만 예외 — caller가 DOCUMENT_EXTRACT_EMPTY로 판단
                    throw new ServiceException(
                        ErrorCode.DOCUMENT_EXTRACT_FAILED,
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "OCR 텍스트 추출에 실패했습니다."
                    );
                }
                break;
            } finally {
                // 각 페이지 렌더링 후 즉시 raster 메모리 해제.
                // flush() 없이는 GC가 처리할 때까지 모든 페이지 이미지가 힙에 남아 있을 수 있다.
                if (image != null) image.flush();
            }
        }

        return sb.toString();
    }

    private ITesseract createTesseract() {
        Tesseract tess = new Tesseract();
        if (!tessdataPath.isBlank()) {
            tess.setDatapath(tessdataPath);
        }
        tess.setLanguage(language);
        return tess;
    }
}
