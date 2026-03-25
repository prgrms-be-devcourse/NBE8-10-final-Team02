package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewSessionDetailResponse(
        Long id,
        Long questionSetId,
        String status,
        InterviewSessionCurrentQuestionResponse currentQuestion,
        long totalQuestionCount,
        long answeredQuestionCount,
        long remainingQuestionCount,
        boolean resumeAvailable,
        Instant lastActivityAt,
        Instant startedAt,
        Instant endedAt
) {
}
