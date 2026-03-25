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
 */
@Service
public class DocumentExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExtractionService.class);

    private final DocumentTextExtractor textExtractor;
    private final PiiMaskingService piiMaskingService;
    private final DocumentRepository documentRepository;
    private final Clock clock;
    private final Executor documentTaskExecutor;

    public DocumentExtractionService(
            DocumentTextExtractor textExtractor,
            PiiMaskingService piiMaskingService,
            DocumentRepository documentRepository,
            Clock clock,
            @Qualifier("documentTaskExecutor") Executor documentTaskExecutor) {
        this.textExtractor = textExtractor;
        this.piiMaskingService = piiMaskingService;
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
     * <p>각 repository 메서드가 자체 트랜잭션을 가지므로 별도 트랜잭션 관리가 불필요하다.
     * 단위 테스트에서 직접 호출 가능하도록 package-private으로 선언한다.</p>
     */
    void performExtraction(DocumentUploadedEvent event) {
        log.info("Starting text extraction for document id={}", event.documentId());

        documentRepository.findById(event.documentId()).ifPresentOrElse(doc -> {
            try {
                String rawText = textExtractor.extract(event.storagePath(), event.mimeType());

                // PII 마스킹: 실패 시 원문을 그대로 저장 (추출 자체는 성공이므로 FAILED 처리하지 않음)
                String textToSave;
                try {
                    textToSave = piiMaskingService.mask(rawText);
                } catch (Exception maskEx) {
                    log.warn("PII masking failed for document id={}, saving raw text: {}",
                        event.documentId(), maskEx.getMessage());
                    textToSave = rawText;
                }

                doc.markExtracted(textToSave, clock.instant());
                documentRepository.save(doc);
                log.info("Text extraction succeeded for document id={}", event.documentId());
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
}
