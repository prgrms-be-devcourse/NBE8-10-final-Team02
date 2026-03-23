package com.back.backend.domain.document.service;

import com.back.backend.domain.document.dto.request.UploadDocumentRequest;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.entity.UserStatus;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void uploadDocument_success() {
        given(documentRepository.countByUserId(1L)).willReturn(0);
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser()));
        given(clock.instant()).willReturn(Instant.parse("2026-03-23T00:00:00Z"));
        given(documentRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(documentService.uploadDocument(1L, new UploadDocumentRequest(
                "resume",
                new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[1024])
        )))
                .extracting("documentType", "originalFileName", "mimeType", "fileSizeBytes", "extractStatus", "uploadedAt")
                .containsExactly(
                        "resume",
                        "resume.pdf",
                        "application/pdf",
                        1024L,
                        "pending",
                        Instant.parse("2026-03-23T00:00:00Z")
                );
    }

    @Test
    void uploadDocument_failWhenInvalidDocumentType() {
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> documentService.uploadDocument(1L, new UploadDocumentRequest(
                "portfolio",
                new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[1024])
        )))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> {
                    ServiceException serviceException = (ServiceException) exception;
                    assertThat(serviceException.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(serviceException.getFieldErrors()).singleElement()
                            .extracting("field", "reason")
                            .containsExactly("documentType", "invalid");
                });
    }

    @Test
    void uploadDocument_failWhenInvalidMimeType() {
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> documentService.uploadDocument(1L, new UploadDocumentRequest(
                "resume",
                new MockMultipartFile("file", "photo.png", "image/png", new byte[1024])
        )))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_INVALID_TYPE));
    }

    @Test
    void uploadDocument_failWhenFileTooLarge() {
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser()));
        byte[] oversizeBytes = new byte[(int) DocumentService.MAX_FILE_SIZE_BYTES + 1];

        assertThatThrownBy(() -> documentService.uploadDocument(1L, new UploadDocumentRequest(
                "resume",
                new MockMultipartFile("file", "resume.pdf", "application/pdf", oversizeBytes)
        )))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_FILE_TOO_LARGE));
    }

    @Test
    void uploadDocument_failWhenExceedsDocumentCount() {
        given(documentRepository.countByUserId(1L)).willReturn(DocumentService.MAX_DOCUMENT_COUNT);
        given(userRepository.findById(1L)).willReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> documentService.uploadDocument(1L, new UploadDocumentRequest(
                "resume",
                new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[1024])
        )))
                .isInstanceOf(ServiceException.class)
                .satisfies(exception -> assertThat(((ServiceException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_UPLOAD_FAILED));
    }

    private User activeUser() {
        return User.builder()
                .email("tester@example.com")
                .displayName("tester")
                .status(UserStatus.ACTIVE)
                .build();
    }
}
