package com.back.backend.domain.document.controller;

import com.back.backend.document.dto.DocumentResponse;
import com.back.backend.document.service.DocumentService;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 문서 관련 API endpoint를 제공하는 controller.
 * 업로드(POST), 목록 조회(GET), 단건 조회(GET /{id}), 삭제(DELETE /{id})기능.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // TODO: auth 구현 후 Security Context에서 userId 추출
    private static final Long PLACEHOLDER_USER_ID = 1L;

    /**
     * 포트폴리오 문서를 업로드한다.
     *
     * <p>허용 파일 형식: PDF, DOCX, Markdown / 최대 크기: 10MB / 사용자당 최대 5개</p>
     *
     * @param file         업로드할 파일 (multipart)
     * @param documentType 문서 종류. 미전달 시 OTHER로 처리된다.
     * @return 저장된 문서의 메타데이터 및 초기 추출 상태
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", defaultValue = "OTHER") DocumentType documentType) {
        return ApiResponse.success(documentService.upload(PLACEHOLDER_USER_ID, documentType, file));
    }

    // 사용자의 전체 문서 목록을 반환한다 (200 OK)
    @GetMapping
    public ApiResponse<List<DocumentResponse>> getDocuments() {
        return ApiResponse.success(documentService.getDocuments(PLACEHOLDER_USER_ID));
    }

    // 문서 단건 상세 조회 (200 OK / 404 Not Found)
    @GetMapping("/{documentId}")
    public ApiResponse<DocumentResponse> getDocument(@PathVariable Long documentId) {
        return ApiResponse.success(documentService.getDocument(PLACEHOLDER_USER_ID, documentId));
    }

    // 문서 삭제 (204 No Content / 404 Not Found / 409 Conflict - 참조 중)
    @DeleteMapping("/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(@PathVariable Long documentId) {
        documentService.deleteDocument(PLACEHOLDER_USER_ID, documentId);
    }
}
