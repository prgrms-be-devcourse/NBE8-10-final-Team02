package com.back.backend.domain.followup.dto.request;

import com.back.backend.domain.followup.model.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FollowupAnalyzeRequest(
        @NotNull(message = "required")
        QuestionType questionType,
        @NotBlank(message = "required")
        String answerText
) {
}
