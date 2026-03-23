package com.back.backend.domain.document.controller;

import com.back.backend.domain.document.dto.request.UploadDocumentRequest;
import com.back.backend.domain.document.dto.response.DocumentResponse;
import com.back.backend.domain.document.service.DocumentService;
import com.back.backend.global.response.ApiResponse;
import com.back.backend.global.security.auth.CurrentUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
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
    private final CurrentUserResolver currentUserResolver;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DocumentResponse> uploadDocument(
            @RequestParam("documentType") String documentType,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        long userId = currentUserResolver.resolve(authentication).id();
        UploadDocumentRequest request = new UploadDocumentRequest(documentType, file);
        return ApiResponse.success(documentService.uploadDocument(userId, request));
    }
}
