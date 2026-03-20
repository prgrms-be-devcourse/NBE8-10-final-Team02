package com.back.backend.document.service;

import com.back.backend.document.dto.DocumentResponse;
import com.back.backend.document.repository.DocumentRepository;
import com.back.backend.document.storage.DocumentStorageService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

// [Unit Test 계층]
// - Spring context 없이 실행 (빠름)
// - 외부 의존성은 @Mock으로 대체
// - 순수 비즈니스 로직만 검증
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentStorageService documentStorageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private DocumentService documentService;

    // --- validateUpload ---

    @Test
    void validateUpload_success() {
        given(documentRepository.countByUserId(1L)).willReturn(0);

        assertThatCode(() ->
                documentService.validateUpload(1L, "application/pdf", 1024L)
        ).doesNotThrowAnyException();
    }

    @Test
    void validateUpload_failWhenInvalidMimeType() {
        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "image/png", 1024L)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_INVALID_TYPE));
    }

    @Test
    void validateUpload_failWhenFileTooLarge() {
        long overLimit = DocumentService.MAX_FILE_SIZE_BYTES + 1;

        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "application/pdf", overLimit)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_FILE_TOO_LARGE));
    }

    @Test
    void validateUpload_failWhenExceedsDocumentCount() {
        given(documentRepository.countByUserId(1L)).willReturn(DocumentService.MAX_DOCUMENT_COUNT);

        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "application/pdf", 1024L)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_UPLOAD_FAILED));
    }

    // --- upload ---

    @Test
    void upload_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[1024]);

        User mockUser = User.builder()
                .email("test@example.com")
                .displayName("tester")
                .status(UserStatus.ACTIVE)
                .build();

        Document saved = Document.builder()
                .user(mockUser)
                .documentType(DocumentType.RESUME)
                .originalFileName("resume.pdf")
                .storagePath("uploads/uuid_resume.pdf")
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.PENDING)
                .uploadedAt(FIXED_NOW)
                .build();

        given(documentRepository.countByUserId(1L)).willReturn(0);
        given(documentStorageService.store(file)).willReturn("uploads/uuid_resume.pdf");
        given(userRepository.getReferenceById(1L)).willReturn(mockUser);
        given(clock.instant()).willReturn(FIXED_NOW);
        given(documentRepository.save(any())).willReturn(saved);

        DocumentResponse result = documentService.upload(1L, DocumentType.RESUME, file);

        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.extractStatus()).isEqualTo(DocumentExtractStatus.PENDING.getValue());
        assertThat(result.originalFileName()).isEqualTo("resume.pdf");
    }

    @Test
    void upload_failWhenStorageFails() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "resume.pdf", "application/pdf", new byte[1024]);

        given(documentRepository.countByUserId(1L)).willReturn(0);
        willThrow(new ServiceException(
                ErrorCode.DOCUMENT_UPLOAD_FAILED,
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "파일 저장에 실패했습니다.",
                true
        )).given(documentStorageService).store(file);

        assertThatThrownBy(() ->
                documentService.upload(1L, DocumentType.RESUME, file)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_UPLOAD_FAILED));
    }
}