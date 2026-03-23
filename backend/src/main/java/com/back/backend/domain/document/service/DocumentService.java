package com.back.backend.domain.document.service;

import com.back.backend.domain.document.dto.request.UploadDocumentRequest;
import com.back.backend.domain.document.dto.response.DocumentResponse;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.document.exception.DocumentFileTooLargeException;
import com.back.backend.domain.document.exception.DocumentInvalidTypeException;
import com.back.backend.domain.document.exception.DocumentUploadLimitExceededException;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.user.entity.User;
import com.back.backend.domain.user.exception.UserNotFoundException;
import com.back.backend.domain.user.repository.UserRepository;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.global.response.FieldErrorDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10MB
    static final int MAX_DOCUMENT_COUNT = 5;
    static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/markdown"
    );

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public DocumentResponse uploadDocument(long userId, UploadDocumentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        MultipartFile file = request.file();
        DocumentType documentType = parseDocumentType(request.documentType());
        validateUpload(userId, file.getContentType(), file.getSize());

        Instant uploadedAt = Instant.now(clock);
        Document document = Document.builder()
                .user(user)
                .documentType(documentType)
                .originalFileName(originalFileName(file))
                .storagePath(generateStoragePath(userId, file.getOriginalFilename()))
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .extractStatus(DocumentExtractStatus.PENDING)
                .uploadedAt(uploadedAt)
                .build();

        return DocumentResponse.from(documentRepository.save(document));
    }

    private DocumentType parseDocumentType(String documentType) {
        try {
            return DocumentType.fromValue(documentType);
        } catch (IllegalArgumentException exception) {
            throw new ServiceException(
                    ErrorCode.REQUEST_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST,
                    "요청 값을 다시 확인해주세요.",
                    false,
                    List.of(new FieldErrorDetail("documentType", "invalid"))
            );
        }
    }

    private void validateUpload(Long userId, String mimeType, long fileSizeBytes) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new DocumentInvalidTypeException();
        }
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new DocumentFileTooLargeException();
        }
        if (documentRepository.countByUserId(userId) >= MAX_DOCUMENT_COUNT) {
            throw new DocumentUploadLimitExceededException();
        }
    }

    private String originalFileName(MultipartFile file) {
        if (StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }

        return file.getName();
    }

    private String generateStoragePath(long userId, String originalFileName) {
        String extension = fileExtension(originalFileName);
        String storedFileName = UUID.randomUUID().toString();

        if (!extension.isEmpty()) {
            storedFileName += "." + extension;
        }

        return "documents/" + userId + "/" + storedFileName;
    }

    private String fileExtension(String originalFileName) {
        if (!StringUtils.hasText(originalFileName)) {
            return "";
        }

        int extensionIndex = originalFileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == originalFileName.length() - 1) {
            return "";
        }

        return originalFileName.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
    }
}
