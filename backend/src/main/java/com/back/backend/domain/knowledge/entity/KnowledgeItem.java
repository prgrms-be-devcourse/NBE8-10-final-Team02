package com.back.backend.domain.knowledge.entity;

import com.back.backend.global.jpa.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@Table(
        name = "knowledge_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_knowledge_items",
                columnNames = {"source_key", "file_path", "title"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeItem extends BaseTimeEntity {

    @Column(name = "source_key", nullable = false, length = 100)
    private String sourceKey;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    public static KnowledgeItem create(String sourceKey, String filePath, String title,
                                       String content, String contentHash) {
        return KnowledgeItem.builder()
                .sourceKey(sourceKey)
                .filePath(filePath)
                .title(title)
                .content(content)
                .contentHash(contentHash)
                .build();
    }

    public void update(String content, String contentHash) {
        this.content = content;
        this.contentHash = contentHash;
    }
}
