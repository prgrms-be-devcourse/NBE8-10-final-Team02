package com.back.backend.document.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import com.back.backend.user.entity.User;
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

@Getter
@Entity
@Builder
@Table(name = "documents")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Document extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Convert(converter = DocumentTypeConverter.class)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Convert(converter = DocumentExtractStatusConverter.class)
    @Column(name = "extract_status", nullable = false)
    private DocumentExtractStatus extractStatus;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "extracted_at")
    private Instant extractedAt;
}
