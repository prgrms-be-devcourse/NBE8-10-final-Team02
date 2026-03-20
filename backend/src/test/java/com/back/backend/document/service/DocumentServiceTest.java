package com.back.backend.document.service;

import com.back.backend.document.repository.DocumentRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

// [Unit Test 계층]
// - Spring context 없이 실행 (빠름)
// - 외부 의존성은 @Mock으로 대체
// - 순수 비즈니스 로직만 검증
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private DocumentService documentService;

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
}
