package com.back.backend.domain.application.repository;

import com.back.backend.domain.application.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {

    Optional<Application> findByIdAndUserId(Long applicationId, Long userId);

    List<Application> findAllByUserIdOrderByCreatedAtDesc(Long userId);
}
