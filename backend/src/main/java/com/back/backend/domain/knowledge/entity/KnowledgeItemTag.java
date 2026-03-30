package com.back.backend.domain.knowledge.entity;

import com.back.backend.global.jpa.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
        name = "knowledge_item_tags",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_knowledge_item_tags",
                columnNames = {"knowledge_item_id", "knowledge_tag_id"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class KnowledgeItemTag extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_item_id", nullable = false)
    private KnowledgeItem item;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "knowledge_tag_id", nullable = false)
    private KnowledgeTag tag;

    public static KnowledgeItemTag of(KnowledgeItem item, KnowledgeTag tag) {
        return KnowledgeItemTag.builder()
                .item(item)
                .tag(tag)
                .build();
    }
}
