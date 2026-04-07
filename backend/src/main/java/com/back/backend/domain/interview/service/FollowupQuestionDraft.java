package com.back.backend.domain.interview.service;

import com.back.backend.domain.interview.entity.DifficultyLevel;
import com.back.backend.domain.interview.entity.InterviewQuestionType;

public record FollowupQuestionDraft(
        InterviewQuestionType questionType,
        DifficultyLevel difficultyLevel,
        String questionText
) {

    public FollowupQuestionDraft {
        if (questionType == null) {
            throw new IllegalArgumentException("questionType must not be null");
        }
        if (difficultyLevel == null) {
            throw new IllegalArgumentException("difficultyLevel must not be null");
        }
        if (questionText == null || questionText.isBlank()) {
            throw new IllegalArgumentException("questionText must not be blank");
        }

        questionText = questionText.trim();
    }
}
