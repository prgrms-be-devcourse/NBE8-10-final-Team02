package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record QuestionSetSummaryResponse(
    Long questionSetId,
    Long applicationId,
    String title,
    int questionCount,
    String difficultyLevel,
    Instant createdAt
) {
}
