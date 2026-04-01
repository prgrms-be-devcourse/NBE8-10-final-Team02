package com.back.backend.global.exception;

import com.back.backend.global.response.FieldErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 서비스 계층에서 발생하는 비즈니스 예외 기반 클래스
 * retryAfterSeconds: rate limit 등 재시도 대기 시간이 필요한 경우에만 설정 (그 외 null)
 */
public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final boolean retryable;
    private final List<FieldErrorDetail> fieldErrors;
    /** 재시도까지 기다려야 하는 초. null이면 클라이언트에 전달하지 않음. */
    private final Integer retryAfterSeconds;

    public ServiceException(ErrorCode errorCode, HttpStatus status, String message) {
        this(errorCode, status, message, false, null);
    }

    public ServiceException(ErrorCode errorCode, HttpStatus status, String message, boolean retryable) {
        this(errorCode, status, message, retryable, null);
    }

    public ServiceException(
            ErrorCode errorCode,
            HttpStatus status,
            String message,
            boolean retryable,
            List<FieldErrorDetail> fieldErrors
    ) {
        this(errorCode, status, message, retryable, null, fieldErrors);
    }

    /**
     * retryAfterSeconds를 포함하는 완전한 생성자
     *
     * @param retryAfterSeconds 재시도 대기 초 (null 허용)
     */
    public ServiceException(
            ErrorCode errorCode,
            HttpStatus status,
            String message,
            boolean retryable,
            Integer retryAfterSeconds,
            List<FieldErrorDetail> fieldErrors
    ) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.retryable = retryable;
        this.retryAfterSeconds = retryAfterSeconds;
        this.fieldErrors = fieldErrors == null ? null : List.copyOf(fieldErrors);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public List<FieldErrorDetail> getFieldErrors() {
        return fieldErrors;
    }

    /** rate limit 응답 시 클라이언트에 전달할 재시도 대기 초. null이면 생략. */
    public Integer getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
