package com.back.backend.domain.document.dto.request;

import org.springframework.web.multipart.MultipartFile;

public record UploadDocumentRequest(
        String documentType,
        MultipartFile file
) {
}
