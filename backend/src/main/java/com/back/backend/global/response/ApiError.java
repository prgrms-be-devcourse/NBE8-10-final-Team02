package com.back.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors,
        boolean retryable
) {
}
