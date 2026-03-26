package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.ApplicationSourceDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationSourceDocumentBindingRepository extends JpaRepository<ApplicationSourceDocument, Long> {

    void deleteByApplicationId(Long applicationId);

    long countByApplicationId(Long applicationId);

    List<ApplicationSourceDocument> findAllByApplicationId(Long applicationId);
}
