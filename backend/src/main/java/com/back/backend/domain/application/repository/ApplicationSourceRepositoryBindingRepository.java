package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.ApplicationSourceRepository;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationSourceRepositoryBindingRepository extends JpaRepository<ApplicationSourceRepository, Long> {

    void deleteByApplicationId(Long applicationId);

    long countByApplicationId(Long applicationId);
}
