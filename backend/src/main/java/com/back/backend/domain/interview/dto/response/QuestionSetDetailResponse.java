package com.back.backend.domain.interview.dto.response;

import java.time.Instant;
import java.util.List;

public record QuestionSetDetailResponse(
    Long questionSetId,
    Long applicationId,
    String title,
    int questionCount,
    String difficultyLevel,
    Instant createdAt,
    List<InterviewQuestionResponse> questions
) {
}
