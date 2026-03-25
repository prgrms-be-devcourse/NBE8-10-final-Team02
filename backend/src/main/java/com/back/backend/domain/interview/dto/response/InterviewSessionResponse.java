package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewSessionResponse(
        Long id,
        Long questionSetId,
        String status,
        Integer totalScore,
        String summaryFeedback,
        Instant startedAt,
        Instant endedAt
) {
}
