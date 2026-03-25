package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewAnswerTag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerTagRepository extends JpaRepository<InterviewAnswerTag, Long> {
}
