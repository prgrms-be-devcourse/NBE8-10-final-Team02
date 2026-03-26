package com.back.backend.domain.interview.dto.response;

public record InterviewSessionCurrentQuestionResponse(
        Long id,
        Integer questionOrder,
        String questionType,
        String difficultyLevel,
        String questionText
) {
}
