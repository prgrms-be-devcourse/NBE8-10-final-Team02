package com.back.backend.domain.document.service;

import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.document.event.DocumentUploadedEvent;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

/**
 * DocumentExtractionService 단위 테스트.
 *
 * <p>추출 로직은 {@code performExtraction}에 담겨 있으며, 이 메서드를 직접 호출해 검증한다.
 * 비동기 디스패치({@code onDocumentUploaded} → executor 제출)는 통합 테스트 영역이므로 여기서는 제외한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class DocumentExtractionServiceTest {

    static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private DocumentTextExtractor textExtractor;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private Clock clock;

    // executor mock — onDocumentUploaded에서 사용하지만 performExtraction 직접 호출 시에는 무관
    @Mock
    private Executor documentTaskExecutor;

    @InjectMocks
    private DocumentExtractionService extractionService;

    // =========================================================
    // 정상 추출 → SUCCESS
    // =========================================================

    @Test
    void onDocumentUploaded_updatesStatusToSuccess() {
        Document doc = pendingDocument();
        given(documentRepository.findById(1L)).willReturn(Optional.of(doc));
        given(textExtractor.extract("uploads/test.pdf", "application/pdf")).willReturn("extracted text");
        given(clock.instant()).willReturn(FIXED_NOW);

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        assertThat(doc.getExtractStatus()).isEqualTo(DocumentExtractStatus.SUCCESS);
        assertThat(doc.getExtractedText()).isEqualTo("extracted text");
        assertThat(doc.getExtractedAt()).isEqualTo(FIXED_NOW);
        then(documentRepository).should().save(doc);
    }

    // =========================================================
    // 추출 실패 (ServiceException) → FAILED
    // =========================================================

    @Test
    void onDocumentUploaded_updatesStatusToFailedOnServiceException() {
        Document doc = pendingDocument();
        given(documentRepository.findById(1L)).willReturn(Optional.of(doc));
        given(textExtractor.extract(any(), any())).willThrow(
            new ServiceException(ErrorCode.DOCUMENT_EXTRACT_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT, "추출 실패"));

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        assertThat(doc.getExtractStatus()).isEqualTo(DocumentExtractStatus.FAILED);
        assertThat(doc.getExtractedText()).isNull();
        then(documentRepository).should().save(doc);
    }

    // =========================================================
    // 예상치 못한 예외 → FAILED
    // =========================================================

    @Test
    void onDocumentUploaded_updatesStatusToFailedOnUnexpectedException() {
        Document doc = pendingDocument();
        given(documentRepository.findById(1L)).willReturn(Optional.of(doc));
        given(textExtractor.extract(any(), any())).willThrow(new RuntimeException("unexpected"));

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        assertThat(doc.getExtractStatus()).isEqualTo(DocumentExtractStatus.FAILED);
        then(documentRepository).should().save(doc);
    }

    // =========================================================
    // 문서 없을 때 → 조용히 무시
    // =========================================================

    @Test
    void onDocumentUploaded_doesNothingWhenDocumentNotFound() {
        given(documentRepository.findById(1L)).willReturn(Optional.empty());

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        then(textExtractor).shouldHaveNoInteractions();
        then(documentRepository).should().findById(1L);
        then(documentRepository).shouldHaveNoMoreInteractions();
    }

    // =========================================================
    // Test helpers
    // =========================================================

    private Document pendingDocument() {
        User user = User.builder()
            .email("test@example.com")
            .displayName("tester")
            .status(UserStatus.ACTIVE)
            .build();
        return Document.builder()
            .user(user)
            .documentType(DocumentType.RESUME)
            .originalFileName("test.pdf")
            .storagePath("uploads/test.pdf")
            .mimeType("application/pdf")
            .fileSizeBytes(1024L)
            .extractStatus(DocumentExtractStatus.PENDING)
            .uploadedAt(FIXED_NOW)
            .build();
    }
}
