package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, Long> {

    long countBySessionId(Long sessionId);

    Optional<InterviewAnswer> findTopBySessionIdOrderByCreatedAtDesc(Long sessionId);
}
