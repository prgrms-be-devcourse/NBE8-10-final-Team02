package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    boolean existsByQuestionSetId(Long questionSetId);
}
