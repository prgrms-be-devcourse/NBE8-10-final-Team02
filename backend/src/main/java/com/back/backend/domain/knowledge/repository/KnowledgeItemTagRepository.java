package com.back.backend.domain.knowledge.repository;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.entity.KnowledgeItemTag;
import com.back.backend.domain.knowledge.entity.KnowledgeTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeItemTagRepository extends JpaRepository<KnowledgeItemTag, Long> {

    boolean existsByItemAndTag(KnowledgeItem item, KnowledgeTag tag);
}
