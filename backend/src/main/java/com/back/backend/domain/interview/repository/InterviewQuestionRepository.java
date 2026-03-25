package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {

    long countByQuestionSetId(Long questionSetId);

    Optional<InterviewQuestion> findTopByQuestionSetIdOrderByQuestionOrderDesc(Long questionSetId);

    Optional<InterviewQuestion> findByQuestionSetIdAndQuestionOrder(Long questionSetId, Integer questionOrder);

    Optional<InterviewQuestion> findByIdAndQuestionSetId(Long id, Long questionSetId);

    List<InterviewQuestion> findAllByQuestionSetIdOrderByQuestionOrderAsc(Long questionSetId);
}
