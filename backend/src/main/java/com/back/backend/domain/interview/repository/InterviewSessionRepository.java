package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    boolean existsByQuestionSetId(Long questionSetId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<InterviewSessionStatus> statuses);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);
}
