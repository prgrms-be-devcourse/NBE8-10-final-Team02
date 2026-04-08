package com.back.backend.domain.document.service;

import com.back.backend.domain.application.repository.ApplicationSourceDocumentRepository;
import com.back.backend.domain.document.dto.DocumentResponse;
import com.back.backend.domain.document.event.DocumentUploadedEvent;
import com.back.backend.domain.document.repository.DocumentRepository;
import com.back.backend.domain.document.storage.DocumentStorageService;
import com.back.backend.domain.document.entity.Document;
import com.back.backend.domain.document.entity.DocumentExtractStatus;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.domain.user.entity.User;
import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import com.back.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * 문서 업로드 비즈니스 로직을 담당하는 service.
 *
 * <p>업로드 흐름: 유효성 검사 → 파일 저장(storage) → Document entity 생성 및 DB 저장</p>
 * <p>텍스트 추출은 이 service의 범위 밖이며, 업로드 후 별도 파이프라인이 처리한다.</p>
 */
@Service
@RequiredArgsConstructor
public class DocumentService {

    /** 허용되는 최대 파일 크기 (50MB). */
    public static final long MAX_MB = 50;
    public static final long MAX_FILE_SIZE_BYTES = MAX_MB * 1024 * 1024L;

    /** 사용자 1명이 보유할 수 있는 최대 문서 수. */
    public static final int MAX_DOCUMENT_COUNT = 5;

    /** 업로드 허용 MIME type 목록: PDF, DOCX, Markdown, Text.
     * Markdown은 OS/브라우저마다 MIME type이 달라 여러 값을 허용한다.
     * (text/markdown: RFC 7763 표준, text/x-markdown: 구형 클라이언트,
     *  text/plain: TXT 파일, application/octet-stream: Windows 등에서 미등록 MIME type)
     * DOCX는 ZIP 기반 포맷이라 일부 클라이언트가 application/zip 또는
     * application/x-zip-compressed 로 전송한다. 확장자 검사(.docx)가 최종 게이트 역할을
     * 하므로 이 값들을 허용해도 임의의 ZIP 파일 업로드는 차단된다. */
    static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/zip",
        "application/x-zip-compressed",
        "text/markdown",
        "text/x-markdown",
        "text/plain",
        "application/octet-stream"
    );

    /** 업로드 허용 파일 확장자 목록. MIME type과 함께 이중 검증한다. */
    static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".md", ".txt");

    private final DocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final UserRepository userRepository;
    private final ApplicationSourceDocumentRepository applicationSourceDocumentRepository;
    private final Clock clock;
    // 업로드 완료 후 텍스트 추출 파이프라인을 비동기로 트리거하기 위한 publisher
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 업로드 전 유효성을 검사한다.
     *
     * <p>다음 네 가지 조건 중 하나라도 위반하면 {@link ServiceException}을 던진다.</p>
     * <ul>
     *   <li>허용되지 않는 MIME type</li>
     *   <li>허용되지 않는 파일 확장자</li>
     *   <li>파일 크기가 50MB(혹은 정해진 다른 크기) 초과</li>
     *   <li>해당 사용자의 문서가 이미 5개(혹은 정해진 다른 크기) 이상</li>
     * </ul>
     *
     * @param userId        업로드 요청 사용자 ID
     * @param mimeType      파일의 MIME type
     * @param filename      원본 파일명 (확장자 검증에 사용)
     * @param fileSizeBytes 파일 크기 (bytes)
     */
    public void validateUpload(Long userId, String mimeType, String filename, long fileSizeBytes) {
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new ServiceException(
                ErrorCode.DOCUMENT_INVALID_TYPE,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "지원하지 않는 파일 형식입니다."
            );
        }
        String ext = filename != null && filename.contains(".")
            ? filename.substring(filename.lastIndexOf('.')).toLowerCase()
            : "";
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
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
                "파일 크기는 " + MAX_MB + "MB를 초과할 수 없습니다."
            );
        }
        if (documentRepository.countByUserId(userId) >= MAX_DOCUMENT_COUNT) {
            throw new ServiceException(
                ErrorCode.DOCUMENT_UPLOAD_FAILED,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "문서는 최대 " + MAX_DOCUMENT_COUNT + "개까지 업로드할 수 있습니다."
            );
        }
    }

    /**
     * 문서를 업로드하고 저장된 결과를 반환한다.
     *
     * <p>유효성 검사 통과 후 파일을 storage에 저장하고,
     * {@code extractStatus = PENDING} 상태로 Document를 DB에 persist한다.</p>
     *
     * <p>User는 실제 조회 없이 {@code getReferenceById}로 proxy만 생성한다.
     * user_id FK 저장만 필요하므로 불필요한 SELECT를 방지한다.</p>
     *
     * @param userId       업로드 요청 사용자 ID
     * @param documentType 문서 종류
     * @param file         업로드할 파일
     * @return 저장된 Document의 응답 DTO
     */
    @Transactional
    public DocumentResponse upload(Long userId, DocumentType documentType, MultipartFile file) {
        validateUpload(userId, file.getContentType(), file.getOriginalFilename(), file.getSize());

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

        Document saved = documentRepository.save(document);

        // 트랜잭션 커밋 후 DocumentExtractionService가 이 이벤트를 수신해 비동기로 텍스트 추출을 시작한다
        eventPublisher.publishEvent(
            new DocumentUploadedEvent(saved.getId(), saved.getStoragePath(), saved.getMimeType(), saved.getUser().getId()));

        return DocumentResponse.from(saved);
    }

    // 사용자의 전체 문서 목록을 반환한다
    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocuments(Long userId) {
        return documentRepository.findAllByUserId(userId).stream()
            .map(DocumentResponse::from)
            .toList();
    }

    // 문서 단건 조회: userId를 함께 조회 조건으로 사용해 다른 사용자 접근을 차단한다
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.DOCUMENT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "문서를 찾을 수 없습니다."
            ));
        return DocumentResponse.from(document);
    }

    // 문서 삭제: 존재 여부 확인 → 지원 단위 참조 여부 확인 → 삭제 순서로 진행한다
    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.DOCUMENT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "문서를 찾을 수 없습니다."
            ));
        // 지원 단위(application)에서 이 문서를 소스로 사용 중이면 삭제 불가
        if (applicationSourceDocumentRepository.existsByDocumentId(documentId)) {
            throw new ServiceException(
                ErrorCode.DOCUMENT_IN_USE,
                HttpStatus.CONFLICT,
                "지원 단위에서 참조 중인 문서는 삭제할 수 없습니다."
            );
        }
        documentRepository.delete(document);
        // DB 삭제 후 물리 파일도 삭제 (실패 시 로그만 남기고 예외를 던지지 않음)
        documentStorageService.delete(document.getStoragePath());
    }

    /**
     * PGroonga 전문 검색(FTS)으로 문서를 검색한다.
     *
     * <p>extracted_text(success 상태 한정)와 original_file_name 양쪽을 단일 쿼리로 OR 검색하고
     * PGroonga relevance score 내림차순으로 정렬한다.
     * PGroonga가 미설치된 환경에서는 repository가 예외를 던질 수 있다.</p>
     *
     * @param userId 검색 대상 사용자 ID
     * @param query  검색 키워드 (한국어, 영어, Fuzzy 지원)
     * @return 검색 결과 DTO 목록 (관련도 내림차순, 최대 20개)
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> search(Long userId, String query) {
        return documentRepository.search(userId, query).stream()
            .map(DocumentResponse::from)
            .toList();
    }

    // 추출된 텍스트를 업데이트한다. 사용자만 자신의 문서를 수정할 수 있다.
    @Transactional
    public DocumentResponse updateExtractedText(Long userId, Long documentId, String extractedText) {
        Document document = documentRepository.findByIdAndUserId(documentId, userId)
            .orElseThrow(() -> new ServiceException(
                ErrorCode.DOCUMENT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "문서를 찾을 수 없습니다."
            ));
        document.setExtractedText(extractedText);
        Document updated = documentRepository.save(document);
        return DocumentResponse.from(updated);
    }
}
