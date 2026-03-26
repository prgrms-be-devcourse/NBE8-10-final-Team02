package com.back.backend.domain.application.dto.request;

public record GenerateAnswersRequest(
    boolean useTemplate,
    boolean regenerate
) {
}
