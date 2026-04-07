package com.back.backend.domain.interview.dto.response;

public record InterviewSessionAnswerSummaryResponse(
        Integer answerOrder,
        String answerText,
        boolean isSkipped
) {
}
