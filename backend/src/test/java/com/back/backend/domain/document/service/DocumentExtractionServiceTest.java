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

import static org.mockito.Mockito.never;

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
    private TextSanitizationService textSanitizationService;

    @Mock
    private SecretMaskingService secretMaskingService;

    @Mock
    private PiiMaskingService piiMaskingService;

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
    void onDocumentUploaded_updatesStatusToSuccessWithMaskedText() {
        Document doc = pendingDocument();
        given(documentRepository.findById(1L)).willReturn(Optional.of(doc));
        given(textExtractor.extract("uploads/test.pdf", "application/pdf")).willReturn("이름: 홍길동");
        given(textSanitizationService.sanitize("이름: 홍길동")).willReturn("이름: 홍길동");
        // 시크릿 마스킹 먼저 — 시크릿 없으므로 원문 그대로
        given(secretMaskingService.mask("이름: 홍길동")).willReturn("이름: 홍길동");
        // PII 마스킹은 시크릿 마스킹 결과를 입력으로 받음
        given(piiMaskingService.mask("이름: 홍길동")).willReturn("이름: 홍*동");
        given(clock.instant()).willReturn(FIXED_NOW);

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        assertThat(doc.getExtractStatus()).isEqualTo(DocumentExtractStatus.SUCCESS);
        assertThat(doc.getExtractedText()).isEqualTo("이름: 홍*동");
        assertThat(doc.getExtractedAt()).isEqualTo(FIXED_NOW);
        then(textSanitizationService).should().sanitize("이름: 홍길동");
        then(secretMaskingService).should().mask("이름: 홍길동");
        then(piiMaskingService).should().mask("이름: 홍길동");
        then(documentRepository).should().save(doc);
    }

    // =========================================================
    // 마스킹 실패 → 원문 저장, SUCCESS 유지
    // =========================================================

    @Test
    void onDocumentUploaded_savesSanitizedTextWhenPiiMaskingFails() {
        Document doc = pendingDocument();
        given(documentRepository.findById(1L)).willReturn(Optional.of(doc));
        given(textExtractor.extract("uploads/test.pdf", "application/pdf")).willReturn("raw text");
        given(textSanitizationService.sanitize("raw text")).willReturn("raw text");
        // 시크릿 마스킹은 성공 (시크릿 없으므로 원문 반환)
        given(secretMaskingService.mask("raw text")).willReturn("raw text");
        // PII 마스킹 실패 → 시크릿 마스킹 결과(raw text)가 저장됨
        given(piiMaskingService.mask("raw text")).willThrow(new RuntimeException("masking error"));
        given(clock.instant()).willReturn(FIXED_NOW);

        extractionService.performExtraction(
            new DocumentUploadedEvent(1L, "uploads/test.pdf", "application/pdf"));

        // 추출 자체는 성공이므로 SUCCESS, 시크릿 마스킹까지 완료된 텍스트가 저장됨
        assertThat(doc.getExtractStatus()).isEqualTo(DocumentExtractStatus.SUCCESS);
        assertThat(doc.getExtractedText()).isEqualTo("raw text");
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
        // 추출 실패 시 sanitize는 호출되지 않음

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
