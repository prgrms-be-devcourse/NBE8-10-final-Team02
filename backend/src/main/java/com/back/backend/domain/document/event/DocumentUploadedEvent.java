package com.back.backend.domain.document.event;

/**
 * 문서 업로드가 완료되어 트랜잭션이 커밋된 후 발행되는 이벤트.
 *
 * <p>{@code DocumentExtractionService}가 이 이벤트를 구독해
 * 비동기로 텍스트 추출을 시작한다.</p>
 *
 * @param documentId  추출 대상 문서의 ID
 * @param storagePath 파일이 저장된 상대 경로 (예: "uploads/uuid_resume.pdf")
 * @param mimeType    파일 MIME type — 추출 방식 선택에 사용
 */
public record DocumentUploadedEvent(Long documentId, String storagePath, String mimeType) {
}
