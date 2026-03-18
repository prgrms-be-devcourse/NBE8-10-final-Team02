package com.back.backend.document.repository;

import com.back.backend.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    int countByUserId(Long userId);
}