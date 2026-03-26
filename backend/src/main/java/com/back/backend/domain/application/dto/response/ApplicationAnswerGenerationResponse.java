package com.back.backend.domain.application.dto.response;

import com.back.backend.domain.application.entity.ApplicationQuestion;

import java.util.List;

public record ApplicationAnswerGenerationResponse(
    Long applicationId,
    int generatedCount,
    boolean regenerate,
    List<AnswerItem> answers
) {

    public record AnswerItem(
        Long questionId,
        String questionText,
        String generatedAnswer,
        String toneOption,
        String lengthOption
    ) {

        public static AnswerItem from(ApplicationQuestion question) {
            return new AnswerItem(
                question.getId(),
                question.getQuestionText(),
                question.getGeneratedAnswer(),
                question.getToneOption() != null ? question.getToneOption().getValue() : null,
                question.getLengthOption() != null ? question.getLengthOption().getValue() : null
            );
        }
    }

    public static ApplicationAnswerGenerationResponse of(
        Long applicationId, boolean regenerate,
        int generatedCount, List<ApplicationQuestion> questions
    ) {
        List<AnswerItem> answers = questions.stream()
            .map(AnswerItem::from)
            .toList();

        return new ApplicationAnswerGenerationResponse(
            applicationId, generatedCount, regenerate, answers
        );
    }
}
