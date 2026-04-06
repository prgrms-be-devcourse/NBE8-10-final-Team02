package com.back.backend.global.response;

import com.back.backend.global.exception.ErrorCode;

import java.util.List;

/**
 * API 오류 응답 최상위 래퍼
 * of() 오버로드: retryAfterSeconds를 명시적으로 받는 버전과 null로 위임하는 기존 버전
 */
public record ApiErrorResponse(
        boolean success,
        ApiError error,
        ApiMeta meta
) {

    /**
     * retryAfterSeconds 없는 기존 호출 사이트와의 호환을 위한 오버로드
     */
    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            boolean retryable,
            List<FieldErrorDetail> fieldErrors
    ) {
        return of(errorCode, message, retryable, fieldErrors, null);
    }

    /**
     * retryAfterSeconds 포함 버전 — rate limit 시 클라이언트에 재시도 대기 시간 전달
     *
     * @param retryAfterSeconds 재시도까지 기다려야 하는 초 (null이면 직렬화 제외됨)
     */
    public static ApiErrorResponse of(
            ErrorCode errorCode,
            String message,
            boolean retryable,
            List<FieldErrorDetail> fieldErrors,
            Integer retryAfterSeconds
    ) {
        List<FieldErrorDetail> normalizedFieldErrors = fieldErrors == null || fieldErrors.isEmpty()
                ? null
                : List.copyOf(fieldErrors);

        return new ApiErrorResponse(
                false,
                new ApiError(errorCode.name(), message, normalizedFieldErrors, retryable, retryAfterSeconds),
                ApiMeta.fromCurrentRequest()
        );
    }
}
