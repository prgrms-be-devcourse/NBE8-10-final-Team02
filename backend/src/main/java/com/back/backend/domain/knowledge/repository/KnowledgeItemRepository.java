package com.back.backend.domain.knowledge.repository;

import com.back.backend.domain.knowledge.entity.KnowledgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
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

    // -- Practice 문제은행 조회용 --

    @Query("SELECT DISTINCT ki FROM KnowledgeItem ki " +
           "JOIN KnowledgeItemTag kit ON kit.item.id = ki.id " +
           "WHERE kit.tag.id IN :tagIds")
    Page<KnowledgeItem> findByTagIds(@Param("tagIds") Collection<Long> tagIds, Pageable pageable);

    @Query("SELECT DISTINCT ki FROM KnowledgeItem ki " +
           "JOIN KnowledgeItemTag kit ON kit.item.id = ki.id " +
           "WHERE kit.tag.id IN :tagIds AND ki.sourceKey = :sourceKey")
    Page<KnowledgeItem> findByTagIdsAndSourceKey(
            @Param("tagIds") Collection<Long> tagIds,
            @Param("sourceKey") String sourceKey,
            Pageable pageable);

    Page<KnowledgeItem> findBySourceKey(String sourceKey, Pageable pageable);

    Page<KnowledgeItem> findBySourceKeyNot(String sourceKey, Pageable pageable);

    @Query("SELECT DISTINCT ki FROM KnowledgeItem ki " +
           "JOIN KnowledgeItemTag kit ON kit.item.id = ki.id " +
           "WHERE kit.tag.id IN :tagIds AND ki.sourceKey <> :sourceKey")
    Page<KnowledgeItem> findByTagIdsAndSourceKeyNot(
            @Param("tagIds") Collection<Long> tagIds,
            @Param("sourceKey") String sourceKey,
            Pageable pageable);

    @Query(value = "SELECT DISTINCT ki.* FROM knowledge_items ki " +
                   "JOIN knowledge_item_tags kit ON kit.knowledge_item_id = ki.id " +
                   "WHERE kit.knowledge_tag_id IN :tagIds " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomByTagIds(@Param("tagIds") Collection<Long> tagIds,
                                           @Param("count") int count);

    @Query(value = "SELECT DISTINCT ki.* FROM knowledge_items ki " +
                   "JOIN knowledge_item_tags kit ON kit.knowledge_item_id = ki.id " +
                   "WHERE kit.knowledge_tag_id IN :tagIds AND ki.source_key = :sourceKey " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomByTagIdsAndSourceKey(@Param("tagIds") Collection<Long> tagIds,
                                                       @Param("sourceKey") String sourceKey,
                                                       @Param("count") int count);

    @Query(value = "SELECT * FROM knowledge_items WHERE source_key = :sourceKey " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomBySourceKey(@Param("sourceKey") String sourceKey,
                                              @Param("count") int count);

    @Query(value = "SELECT * FROM knowledge_items ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandom(@Param("count") int count);

    @Query(value = "SELECT * FROM knowledge_items WHERE source_key <> :sourceKey " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomBySourceKeyNot(@Param("sourceKey") String sourceKey,
                                                  @Param("count") int count);

    @Query(value = "SELECT DISTINCT ki.* FROM knowledge_items ki " +
                   "JOIN knowledge_item_tags kit ON kit.knowledge_item_id = ki.id " +
                   "WHERE kit.knowledge_tag_id IN :tagIds AND ki.source_key <> :sourceKey " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomByTagIdsAndSourceKeyNot(@Param("tagIds") Collection<Long> tagIds,
                                                           @Param("sourceKey") String sourceKey,
                                                           @Param("count") int count);

    // -- 태그 매칭 CS + 전체 인성 결합 조회 (전체 모드 + 태그 필터 시 사용) --

    @Query("SELECT DISTINCT ki FROM KnowledgeItem ki " +
           "LEFT JOIN KnowledgeItemTag kit ON kit.item.id = ki.id " +
           "WHERE kit.tag.id IN :tagIds OR ki.sourceKey = :sourceKey")
    Page<KnowledgeItem> findByTagIdsOrSourceKey(
            @Param("tagIds") Collection<Long> tagIds,
            @Param("sourceKey") String sourceKey,
            Pageable pageable);

    @Query(value = "SELECT DISTINCT ki.* FROM knowledge_items ki " +
                   "LEFT JOIN knowledge_item_tags kit ON kit.knowledge_item_id = ki.id " +
                   "WHERE kit.knowledge_tag_id IN :tagIds OR ki.source_key = :sourceKey " +
                   "ORDER BY RANDOM() LIMIT :count",
           nativeQuery = true)
    List<KnowledgeItem> findRandomByTagIdsOrSourceKey(
            @Param("tagIds") Collection<Long> tagIds,
            @Param("sourceKey") String sourceKey,
            @Param("count") int count);
}
