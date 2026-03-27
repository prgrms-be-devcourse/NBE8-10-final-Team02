package com.back.backend.domain.document.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.domain.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자가 업로드한 포트폴리오 문서를 나타내는 entity.
 *
 * <p>업로드된 파일의 메타데이터와 텍스트 추출 상태를 함께 관리한다.
 * 파일 원본은 {@code storagePath}가 가리키는 스토리지(현재 로컬 디스크)에 저장되며,
 * 이 entity는 그 경로와 추출 결과만 DB에 보관한다.</p>
 *
 * <p>생성 시 {@code extractStatus}는 기본적으로 {@code PENDING}으로 시작하고,
 * 이후 {@code SUCCESS} 또는 {@code FAILED}로 전환됨.</p>
 */
@Getter
@Entity
@Builder
@Table(name = "documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Document extends BaseEntity {

    // 이 문서를 소유한 사용자. 실제 User 조회가 필요할 때만 로딩되도록 LAZY로 설정.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 문서 종류 (RESUME, AWARD, CERTIFICATE, OTHER)
     * Entity 단에선 DocumentType Enum이지만 JPA 컨버터를 사용해 DB에는 lowercase 문자열로 저장됨 (예: "resume").
     */
    @Convert(converter = DocumentTypeConverter.class)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /** 사용자가 업로드할 때의 원본 파일명. 화면 표시. */
    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    /**
     * 실제 파일이 저장된 경로 (예: "uploads/uuid_filename.pdf").
     * 현재는 로컬 디스크 기준이며, 추후 S3 등으로 교체해도 이 필드 형식만 바꾸면 된다.
     */
    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    /** 파일의 MIME type (예: "application/pdf"). 업로드 시 허용 여부 검증에도 사용된다. */
    @Column(name = "mime_type", nullable = false, length = 255)
    private String mimeType;

    /** 파일 크기 (bytes). 최대 10MB 제한 검증에 사용된다. */
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    /**
     * 텍스트 추출 상태.
     * <ul>
     *   <li>PENDING: 업로드 직후, 아직 추출 전</li>
     *   <li>SUCCESS: 텍스트 추출 완료</li>
     *   <li>FAILED: 추출 실패 (파싱 오류 등)</li>
     * </ul>
     * DB에는 lowercase 문자열로 저장된다 (예: "pending").
     */
    @Convert(converter = DocumentExtractStatusConverter.class)
    @Column(name = "extract_status", nullable = false, length = 20)
    private DocumentExtractStatus extractStatus;

    /**
     * 추출된 텍스트 본문. 추출 전이거나 실패한 경우 null이다.
     * AI 분석 파이프라인의 입력으로 사용된다.
     */
    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    /** 파일 업로드 완료 시각. */
    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    /** 텍스트 추출 완료 시각. 추출 전이거나 실패한 경우 null이다. */
    @Column(name = "extracted_at")
    private Instant extractedAt;

    /**
     * 텍스트 추출이 성공한 경우 호출한다.
     * extractStatus를 SUCCESS로, extractedText와 extractedAt을 설정한다.
     *
     * @param text      추출된 텍스트 (마스킹 전 원문; 이후 PII 마스킹 파이프라인에서 처리 예정)
     * @param extractedAt 추출 완료 시각
     */
    public void markExtracted(String text, Instant extractedAt) {
        this.extractedText = text;
        this.extractedAt = extractedAt;
        this.extractStatus = DocumentExtractStatus.SUCCESS;
    }

    /**
     * 텍스트 추출이 실패한 경우 호출한다.
     * extractStatus를 FAILED로 설정하고 텍스트 관련 필드는 null로 유지한다.
     */
    public void markFailed() {
        this.extractStatus = DocumentExtractStatus.FAILED;
        this.extractedText = null;
        this.extractedAt = null;
    }

    /**
     * 추출된 텍스트를 수동으로 편집할 때 호출한다.
     * 사용자가 UI에서 직접 수정한 내용을 저장한다.
     *
     * @param extractedText 사용자가 편집한 텍스트
     */
    public void setExtractedText(String extractedText) {
        this.extractedText = extractedText;
    }
}
