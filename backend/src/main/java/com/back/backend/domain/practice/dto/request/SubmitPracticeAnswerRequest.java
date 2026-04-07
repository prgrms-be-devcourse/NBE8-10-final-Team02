package com.back.backend.domain.practice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubmitPracticeAnswerRequest(
        @NotNull(message = "질문 ID는 필수입니다")
        Long knowledgeItemId,

        @NotBlank(message = "답변은 필수입니다")
        @Size(min = 50, max = 5000, message = "답변은 50자 이상 5000자 이하로 작성해주세요")
        String answerText
) {}
