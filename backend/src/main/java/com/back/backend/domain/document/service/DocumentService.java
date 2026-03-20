package com.back.backend.domain.document.service;

import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.document.dto.DocumentResponse;
import com.back.backend.document.repository.DocumentRepository;
import com.back.backend.document.storage.DocumentStorageService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.Set;

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
    private final DocumentStorageService documentStorageService;
    private final UserRepository userRepository;
    private final Clock clock;

    public void validateUpload(Long userId, String mimeType, long fileSizeBytes) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new ServiceException(
                    ErrorCode.DOCUMENT_INVALID_TYPE,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "지원하지 않는 파일 형식입니다."
            );
        }
        if (fileSizeBytes > MAX_FILE_SIZE_BYTES) {
            throw new ServiceException(
                    ErrorCode.DOCUMENT_FILE_TOO_LARGE,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "파일 크기는 10MB를 초과할 수 없습니다."
            );
        }
        if (documentRepository.countByUserId(userId) >= MAX_DOCUMENT_COUNT) {
            throw new ServiceException(
                    ErrorCode.DOCUMENT_UPLOAD_FAILED,
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "문서는 최대 5개까지 업로드할 수 있습니다."
            );
        }
    }

    @Transactional
    public DocumentResponse upload(Long userId, DocumentType documentType, MultipartFile file) {
        validateUpload(userId, file.getContentType(), file.getSize());

        String storagePath = documentStorageService.store(file);
        User user = userRepository.getReferenceById(userId);

        Document document = Document.builder()
                .user(user)
                .documentType(documentType)
                .originalFileName(file.getOriginalFilename())
                .storagePath(storagePath)
                .mimeType(file.getContentType())
                .fileSizeBytes(file.getSize())
                .extractStatus(DocumentExtractStatus.PENDING)
                .uploadedAt(clock.instant())
                .build();

        return DocumentResponse.from(documentRepository.save(document));
    }
}
