package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewQuestionSetSummaryResponse(
        Long questionSetId,
        Long applicationId,
        String title,
        Integer questionCount,
        String difficultyLevel,
        Instant createdAt
) {
}
