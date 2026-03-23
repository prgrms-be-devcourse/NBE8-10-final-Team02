package com.back.backend.domain.document.dto.response;

import com.back.backend.domain.document.entity.Document;

import java.time.Instant;

public record DocumentResponse(
        Long id,
        String documentType,
        String originalFileName,
        String mimeType,
        Long fileSizeBytes,
        String extractStatus,
        Instant uploadedAt,
        Instant extractedAt,
        String extractedText
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getDocumentType().getValue(),
                document.getOriginalFileName(),
                document.getMimeType(),
                document.getFileSizeBytes(),
                document.getExtractStatus().getValue(),
                document.getUploadedAt(),
                document.getExtractedAt(),
                document.getExtractedText()
        );
    }
}
