package com.back.backend.domain.application.dto.request;

import java.util.List;

public record SaveApplicationQuestionsRequest(
        List<QuestionItem> questions
) {

    public List<QuestionItem> questionsOrEmpty() {
        return questions == null ? List.of() : questions;
    }

    public record QuestionItem(
            Integer questionOrder,
            String questionText,
            String toneOption,
            String lengthOption,
            String emphasisPoint
    ) {
    }
}
