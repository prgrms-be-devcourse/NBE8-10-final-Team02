package com.back.backend.domain.interview.dto.response;

import java.time.Instant;
import java.util.List;

public record InterviewQuestionSetDetailResponse(
        Long questionSetId,
        Long applicationId,
        String title,
        Integer questionCount,
        String difficultyLevel,
        List<InterviewQuestionResponse> questions,
        Instant createdAt
) {
}
