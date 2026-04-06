package com.back.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * API 오류 응답 본문 DTO
 * retryAfterSeconds: 재시도 가능 시점까지 남은 초 (rate limit 등에서만 채워짐, 그 외 null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors,
        boolean retryable,
        Integer retryAfterSeconds
) {
}
