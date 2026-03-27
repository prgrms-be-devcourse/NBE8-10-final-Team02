package com.back.backend.domain.interview.dto.request;

import java.util.List;

public record CreateQuestionSetRequest(
    Long applicationId,
    String title,
    int questionCount,
    String difficultyLevel,
    List<String> questionTypes
) {
}
