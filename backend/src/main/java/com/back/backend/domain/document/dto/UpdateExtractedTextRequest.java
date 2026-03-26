package com.back.backend.domain.document.dto;

/**
 * 추출된 텍스트를 업데이트하는 요청 DTO.
 */
public record UpdateExtractedTextRequest(
        String extractedText
) {
}
