package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.ApplicationQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationQuestionRepository extends JpaRepository<ApplicationQuestion, Long> {

    List<ApplicationQuestion> findAllByApplicationIdOrderByQuestionOrderAsc(Long applicationId);

    void deleteByApplicationId(Long applicationId);
}
