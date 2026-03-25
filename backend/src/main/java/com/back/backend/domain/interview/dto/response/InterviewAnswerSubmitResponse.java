package com.back.backend.domain.interview.dto.response;

import java.time.Instant;

public record InterviewAnswerSubmitResponse(
        Long sessionId,
        Long questionId,
        Integer answerOrder,
        boolean isSkipped,
        Instant submittedAt
) {
}
