package com.back.backend.domain.document.dto;

import com.back.backend.domain.document.entity.Document;

import java.time.Instant;

/**
 * 문서 업로드 응답 DTO.
 *
 * <p>Entity를 직접 노출하지 않고 필요한 필드만 담아 반환한다.
 * {@code storagePath}처럼 내부 구현에 해당하는 필드는 의도적으로 제외했다.</p>
 *
 * <p>{@code extractStatus}, {@code documentType}은 enum의 lowercase 문자열 값으로 직렬화된다
 * (예: "pending", "resume").</p>
 */
public record DocumentResponse(
        Long id,
        String documentType,
        String originalFileName,
        String mimeType,
        Long fileSizeBytes,
        /** 텍스트 추출 상태 ("pending" / "success" / "failed") */
        String extractStatus,
        Instant uploadedAt,
        /** 추출 완료 시각. 추출 전이거나 실패한 경우 null. */
        Instant extractedAt,
        /** 추출된 텍스트 본문. 추출 전이거나 실패한 경우 null. */
        String extractedText
) {

    /**
     * {@link Document} entity로부터 응답 DTO를 생성한다.
     *
     * @param document 저장된 Document entity
     * @return 응답용 DTO
     */
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
