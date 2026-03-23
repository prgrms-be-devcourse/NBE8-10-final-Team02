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

import com.back.backend.application.repository.ApplicationSourceDocumentRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
    private ApplicationSourceDocumentRepository applicationSourceDocumentRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private DocumentService documentService;

    // --- validateUpload ---

    @Test
    void validateUpload_success() {
        given(documentRepository.countByUserId(1L)).willReturn(0);

        assertThatCode(() ->
                documentService.validateUpload(1L, "application/pdf", "resume.pdf", 1024L)
        ).doesNotThrowAnyException();
    }

    @Test
    void validateUpload_failWhenInvalidMimeType() {
        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "image/png", "photo.png", 1024L)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_INVALID_TYPE));
    }

    @Test
    void validateUpload_failWhenInvalidExtension() {
        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "application/pdf", "disguised.exe", 1024L)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_INVALID_TYPE));
    }

    @Test
    void validateUpload_failWhenFileTooLarge() {
        long overLimit = DocumentService.MAX_FILE_SIZE_BYTES + 1;

        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "application/pdf", "resume.pdf", overLimit)
        )
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_FILE_TOO_LARGE));
    }

    @Test
    void validateUpload_failWhenExceedsDocumentCount() {
        given(documentRepository.countByUserId(1L)).willReturn(DocumentService.MAX_DOCUMENT_COUNT);

        assertThatThrownBy(() ->
                documentService.validateUpload(1L, "application/pdf", "resume.pdf", 1024L)
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

    // --- getDocuments ---

    @Test
    void getDocuments_returnsListForUser() {
        User mockUser = user();
        Document doc1 = document(mockUser, DocumentType.RESUME, "resume.pdf");
        Document doc2 = document(mockUser, DocumentType.AWARD, "award.pdf");
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of(doc1, doc2));

        List<DocumentResponse> result = documentService.getDocuments(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).documentType()).isEqualTo(DocumentType.RESUME.getValue());
        assertThat(result.get(1).documentType()).isEqualTo(DocumentType.AWARD.getValue());
    }

    @Test
    void getDocuments_returnsEmptyListWhenNone() {
        given(documentRepository.findAllByUserId(1L)).willReturn(List.of());

        List<DocumentResponse> result = documentService.getDocuments(1L);

        assertThat(result).isEmpty();
    }

    // --- getDocument ---

    @Test
    void getDocument_returnsDocumentWhenFound() {
        User mockUser = user();
        Document doc = document(mockUser, DocumentType.RESUME, "resume.pdf");
        given(documentRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(doc));

        DocumentResponse result = documentService.getDocument(1L, 1L);

        assertThat(result.originalFileName()).isEqualTo("resume.pdf");
        assertThat(result.documentType()).isEqualTo(DocumentType.RESUME.getValue());
    }

    @Test
    void getDocument_throwsWhenNotFound() {
        given(documentRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocument(1L, 99L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    // --- deleteDocument ---

    @Test
    void deleteDocument_deletesWhenNotInUse() {
        User mockUser = user();
        Document doc = document(mockUser, DocumentType.RESUME, "resume.pdf");
        given(documentRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(doc));
        given(applicationSourceDocumentRepository.existsByDocumentId(1L)).willReturn(false);

        documentService.deleteDocument(1L, 1L);

        then(documentRepository).should().delete(doc);
    }

    @Test
    void deleteDocument_throwsWhenNotFound() {
        given(documentRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.deleteDocument(1L, 99L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));
    }

    @Test
    void deleteDocument_throwsWhenDocumentInUse() {
        User mockUser = user();
        Document doc = document(mockUser, DocumentType.RESUME, "resume.pdf");
        given(documentRepository.findByIdAndUserId(1L, 1L)).willReturn(Optional.of(doc));
        given(applicationSourceDocumentRepository.existsByDocumentId(1L)).willReturn(true);

        assertThatThrownBy(() -> documentService.deleteDocument(1L, 1L))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> assertThat(((ServiceException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.DOCUMENT_IN_USE));
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
    
    //helper methods to create default user
    private User user() {
        return User.builder()
                .email("test@example.com")
                .displayName("tester")
                .status(UserStatus.ACTIVE)
                .build();
    }

    //helper methods to create sample document 
    private Document document(User user, DocumentType type, String fileName) {
        return Document.builder()
                .user(user)
                .documentType(type)
                .originalFileName(fileName)
                .storagePath("uploads/" + fileName)
                .mimeType("application/pdf")
                .fileSizeBytes(1024L)
                .extractStatus(DocumentExtractStatus.PENDING)
                .uploadedAt(FIXED_NOW)
                .build();
    }
}