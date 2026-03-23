package com.back.backend.domain.document.repository;


import com.back.backend.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    int countByUserId(Long userId);

    List<Document> findAllByIdInAndUserId(Collection<Long> ids, Long userId);
}
