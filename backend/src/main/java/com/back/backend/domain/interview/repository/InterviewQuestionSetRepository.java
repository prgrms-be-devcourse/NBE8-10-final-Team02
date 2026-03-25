package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewQuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewQuestionSetRepository extends JpaRepository<InterviewQuestionSet, Long> {

    Optional<InterviewQuestionSet> findByIdAndUserId(Long id, Long userId);
}
