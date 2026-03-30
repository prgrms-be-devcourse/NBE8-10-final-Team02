package com.back.backend.domain.knowledge.repository;

import com.back.backend.domain.knowledge.entity.KnowledgeTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KnowledgeTagRepository extends JpaRepository<KnowledgeTag, Long> {

    Optional<KnowledgeTag> findByName(String name);
}
