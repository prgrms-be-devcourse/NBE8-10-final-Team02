package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewSessionCompletionResponse(
        Long sessionId,
        String status,
        Integer totalScore,
        String summaryFeedback,
        Instant endedAt
) {
}
