package com.back.backend.domain.practice.repository;

import com.back.backend.domain.practice.entity.PracticeQuestionType;
import com.back.backend.domain.practice.entity.PracticeSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PracticeSessionRepository extends JpaRepository<PracticeSession, Long> {

    Page<PracticeSession> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<PracticeSession> findAllByUserIdAndQuestionTypeOrderByCreatedAtDesc(
            Long userId, PracticeQuestionType questionType, Pageable pageable);

    Optional<PracticeSession> findByIdAndUserId(Long id, Long userId);
}
