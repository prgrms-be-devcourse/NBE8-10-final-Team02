package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSourceDocumentBindingRepository extends JpaRepository<ApplicationSourceDocument, Long> {

    void deleteByApplicationId(Long applicationId);

    long countByApplicationId(Long applicationId);
}
