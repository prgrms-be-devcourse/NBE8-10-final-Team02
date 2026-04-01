package com.back.backend.domain.interview.repository;

import com.back.backend.domain.interview.entity.InterviewSession;
import com.back.backend.domain.interview.entity.InterviewSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    boolean existsByQuestionSetId(Long questionSetId);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<InterviewSessionStatus> statuses);

    List<InterviewSession> findAllByUserIdOrderByStartedAtDesc(Long userId);

    Optional<InterviewSession> findByIdAndUserId(Long id, Long userId);

    List<InterviewSession> findAllByUserIdAndStatusOrderByEndedAtAsc(Long userId, InterviewSessionStatus status);
}
