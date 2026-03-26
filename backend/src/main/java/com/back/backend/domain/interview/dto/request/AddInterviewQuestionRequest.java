package com.back.backend.domain.interview.dto.request;

public record AddInterviewQuestionRequest(
        String questionText,
        String questionType,
        String difficultyLevel
) {
}
