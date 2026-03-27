package com.back.backend.domain.interview.dto.request;

import java.util.List;

public record CreateInterviewQuestionSetRequest(
        Long applicationId,
        String title,
        Integer questionCount,
        String difficultyLevel,
        List<String> questionTypes
) {
}
