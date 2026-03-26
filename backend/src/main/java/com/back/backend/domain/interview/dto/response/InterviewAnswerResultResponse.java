package com.back.backend.domain.interview.dto.response;

import java.util.List;

public record InterviewAnswerResultResponse(
        Long answerId,
        Long questionId,
        String questionText,
        String answerText,
        Integer score,
        String evaluationRationale,
        List<InterviewFeedbackTagResponse> tags
) {
}
