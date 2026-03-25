package com.back.backend.domain.interview.dto.response;

import java.time.Instant;
import java.util.List;

public record InterviewResultResponse(
        Long sessionId,
        Long questionSetId,
        String status,
        Integer totalScore,
        String summaryFeedback,
        List<InterviewAnswerResultResponse> answers,
        Instant startedAt,
        Instant endedAt
) {
}
