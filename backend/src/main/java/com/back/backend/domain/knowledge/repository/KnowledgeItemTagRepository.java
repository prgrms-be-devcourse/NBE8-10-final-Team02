package com.back.backend.domain.knowledge.repository;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import com.back.backend.domain.knowledge.entity.KnowledgeItemTag;
import com.back.backend.domain.knowledge.entity.KnowledgeTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeItemTagRepository extends JpaRepository<KnowledgeItemTag, Long> {

    boolean existsByItemAndTag(KnowledgeItem item, KnowledgeTag tag);

    @Query("""
            select kit from KnowledgeItemTag kit
            join fetch kit.tag
            where kit.item.id in :itemIds
            """)
    List<KnowledgeItemTag> findAllWithTagByItemIds(@Param("itemIds") Collection<Long> itemIds);
}
