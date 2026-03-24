package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    Optional<InterviewQuestion> findTopByQuestionSetIdOrderByQuestionOrderDesc(Long questionSetId);
}
