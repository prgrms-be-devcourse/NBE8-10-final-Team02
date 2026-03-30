package com.back.backend.domain.knowledge.repository;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Optional;

public interface KnowledgeItemRepository extends JpaRepository<KnowledgeItem, Long> {

    Optional<KnowledgeItem> findBySourceKeyAndFilePathAndTitle(
            String sourceKey, String filePath, String title);

    @Transactional
    @Modifying
    @Query("DELETE FROM KnowledgeItem ki " +
           "WHERE ki.sourceKey = :sourceKey " +
           "AND ki.filePath = :filePath " +
           "AND ki.title NOT IN :titles")
    int deleteBySourceKeyAndFilePathAndTitleNotIn(
            @Param("sourceKey") String sourceKey,
            @Param("filePath") String filePath,
            @Param("titles") Collection<String> titles);
}
