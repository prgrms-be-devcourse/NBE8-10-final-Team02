package com.back.backend.document.controller;

import com.back.backend.document.service.DocumentService;
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

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // TODO: auth 구현 후 Security Context에서 userId 추출
    private static final Long PLACEHOLDER_USER_ID = 1L;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> uploadDocument(@RequestParam("file") MultipartFile file) {
        documentService.validateUpload(PLACEHOLDER_USER_ID, file.getContentType(), file.getSize());
        return ApiResponse.success(null);
    }
}
