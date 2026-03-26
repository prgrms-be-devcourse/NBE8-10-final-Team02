package com.back.backend.domain.interview.dto.request;

public record SubmitInterviewAnswerRequest(
        Long questionId,
        Integer answerOrder,
        String answerText,
        boolean isSkipped
) {
}
