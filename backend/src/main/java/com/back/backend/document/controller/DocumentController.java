package com.back.backend.document.controller;

import com.back.backend.document.dto.DocumentResponse;
import com.back.backend.document.service.DocumentService;
import com.back.backend.domain.document.entity.DocumentType;
import com.back.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 문서 업로드 관련 API endpoint를 제공하는 controller.
 *
 * <p>TODO 현재는 업로드 단일 기능만 존재, 조회/삭제 추가해야 함.</p>
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
}
