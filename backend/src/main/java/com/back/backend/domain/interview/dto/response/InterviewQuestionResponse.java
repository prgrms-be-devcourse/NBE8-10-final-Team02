package com.back.backend.domain.interview.dto.response;

public record InterviewQuestionResponse(
        Long id,
        Integer questionOrder,
        String questionType,
        String difficultyLevel,
        String questionText,
        Long parentQuestionId,
        Long sourceApplicationQuestionId
) {
}
