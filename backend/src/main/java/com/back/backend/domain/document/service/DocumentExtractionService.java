package com.back.backend.domain.document.service;

import com.back.backend.domain.document.event.DocumentUploadedEvent;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.concurrent.Executor;

/**
 * 문서 업로드 완료 이벤트를 수신해 텍스트 추출 파이프라인을 실행하는 서비스.
 *
 * <p>처리 순서:</p>
 * <ol>
 *   <li>{@link DocumentUploadedEvent} 수신 (트랜잭션 커밋 후)</li>
 *   <li>{@code documentTaskExecutor}에 추출 작업을 제출해 비동기 실행</li>
 *   <li>텍스트 추출 후 {@link PiiMaskingService}로 PII 마스킹</li>
 *   <li>성공 시: {@code extract_status = SUCCESS}, 마스킹된 텍스트를 {@code extracted_text}에 저장</li>
 *   <li>실패 시: {@code extract_status = FAILED} 저장, 오류 로그</li>
 * </ol>
 *
 * <p><b>Spring 6.2+ 제약</b>: {@code @TransactionalEventListener}와 {@code @Async}를
 * 동일 메서드에 조합하면 {@code IllegalStateException}이 발생한다.
 * 따라서 이벤트 리스너 내에서 {@code Executor}에 직접 제출하는 방식을 사용한다.</p>
 *
 * <h3>성능 모니터링</h3>
 * <p>각 처리 단계(추출 → 정제 → 시크릿 마스킹 → PII 마스킹)의 소요 시간을 INFO 로그로 기록한다.
 * 이를 통해 병목 단계를 파악하고 최적화 우선순위를 결정할 수 있다.</p>
 */
@Service
public class DocumentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractionService.class);

    private final DocumentTextExtractor textExtractor;
    private final TextSanitizationService textSanitizationService;
    private final PiiMaskingService piiMaskingService;
    private final SecretMaskingService secretMaskingService;
    private final DocumentRepository documentRepository;
    private final Clock clock;
    private final Executor documentTaskExecutor;

    public DocumentExtractionService(
            DocumentTextExtractor textExtractor,
            TextSanitizationService textSanitizationService,
            PiiMaskingService piiMaskingService,
            SecretMaskingService secretMaskingService,
            DocumentRepository documentRepository,
            Clock clock,
            @Qualifier("documentTaskExecutor") Executor documentTaskExecutor) {
        this.textExtractor = textExtractor;
        this.textSanitizationService = textSanitizationService;
        this.piiMaskingService = piiMaskingService;
        this.secretMaskingService = secretMaskingService;
        this.documentRepository = documentRepository;
        this.clock = clock;
        this.documentTaskExecutor = documentTaskExecutor;
    }

    /**
     * 업로드 트랜잭션 커밋 후 비동기 추출 작업을 executor에 제출한다.
     *
     * <p>AFTER_COMMIT 단계이므로 업로드 트랜잭션이 롤백되면 이 메서드는 호출되지 않는다.</p>
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        documentTaskExecutor.execute(() -> performExtraction(event));
    }

    /**
     * 실제 텍스트 추출 로직. executor 스레드에서 실행된다.
     *
     * <p>각 단계의 소요 시간을 INFO 레벨로 기록해 병목 구간을 파악한다.
     * 단위 테스트에서 직접 호출 가능하도록 package-private으로 선언한다.</p>
     */
    void performExtraction(DocumentUploadedEvent event) {
        long totalStart = System.nanoTime();
        log.info("Starting text extraction for document id={}", event.documentId());

        documentRepository.findById(event.documentId()).ifPresentOrElse(doc -> {
            try {
                // 1) 텍스트 추출 (PDFBox / POI / plain text)
                long t0 = System.nanoTime();
                String rawText = textExtractor.extract(event.storagePath(), event.mimeType());
                log.info("[doc={}] extract={}ms", event.documentId(), ms(t0));

                // 2) 텍스트 정제: \r\n, zero-width 문자 등 OCR 아티팩트 제거
                long t1 = System.nanoTime();
                String sanitized = textSanitizationService.sanitize(rawText);
                log.info("[doc={}] sanitize={}ms", event.documentId(), ms(t1));

                // 3) 시크릿 마스킹 먼저: PII 패턴이 토큰 일부를 먼저 치환하면 Gitleaks가 못 잡음
                long t2 = System.nanoTime();
                String afterSecret;
                try {
                    afterSecret = secretMaskingService.mask(sanitized);
                } catch (Exception secretEx) {
                    log.warn("Secret masking failed for document id={}, using sanitized text: {}",
                        event.documentId(), secretEx.getMessage());
                    afterSecret = sanitized;
                }
                log.info("[doc={}] secret-mask={}ms", event.documentId(), ms(t2));

                // 4) PII 마스킹: 시크릿이 이미 [REDACTED]로 치환된 텍스트에 적용
                long t3 = System.nanoTime();
                String textToSave;
                try {
                    textToSave = piiMaskingService.mask(afterSecret);
                } catch (Exception maskEx) {
                    log.warn("PII masking failed for document id={}, using secret-masked text: {}",
                        event.documentId(), maskEx.getMessage());
                    textToSave = afterSecret;
                }
                log.info("[doc={}] pii-mask={}ms", event.documentId(), ms(t3));

                doc.markExtracted(textToSave, clock.instant());
                documentRepository.save(doc);
                log.info("Text extraction succeeded for document id={} total={}ms",
                    event.documentId(), ms(totalStart));

            } catch (ServiceException e) {
                log.warn("Text extraction failed for document id={}: {}", event.documentId(), e.getMessage());
                doc.markFailed();
                documentRepository.save(doc);
            } catch (Exception e) {
                log.error("Unexpected error during text extraction for document id={}", event.documentId(), e);
                doc.markFailed();
                documentRepository.save(doc);
            }
        }, () -> log.warn("Document not found for extraction, id={}", event.documentId()));
    }

    /** 나노초를 밀리초로 변환 (로그 가독성). */
    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
