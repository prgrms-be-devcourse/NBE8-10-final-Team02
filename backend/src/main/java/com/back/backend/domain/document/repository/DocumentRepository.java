package com.back.backend.domain.document.repository;


import com.back.backend.domain.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    int countByUserId(Long userId);
}
