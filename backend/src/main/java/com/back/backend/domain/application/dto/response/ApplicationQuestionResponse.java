package com.back.backend.domain.application.dto.response;

public record ApplicationQuestionResponse(
        Long id,
        Integer questionOrder,
        String questionText,
        String generatedAnswer,
        String editedAnswer,
        String toneOption,
        String lengthOption,
        String emphasisPoint
) {
}
