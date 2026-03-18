package com.back.backend.global.response;

import com.back.backend.global.exception.ErrorCode;

import java.util.List;

public record ApiErrorResponse(
        boolean success,
        ApiError error,
        ApiMeta meta
) {

    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            boolean retryable,
            List<FieldErrorDetail> fieldErrors
    ) {
        List<FieldErrorDetail> normalizedFieldErrors = fieldErrors == null || fieldErrors.isEmpty()
                ? null
                : List.copyOf(fieldErrors);

        return new ApiErrorResponse(
                false,
                new ApiError(errorCode.name(), message, normalizedFieldErrors, retryable),
                ApiMeta.fromCurrentRequest()
        );
    }
}
