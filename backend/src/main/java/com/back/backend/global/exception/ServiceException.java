package com.back.backend.global.exception;

import com.back.backend.global.response.FieldErrorDetail;
import org.springframework.http.HttpStatus;

import java.util.List;

public class ServiceException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final boolean retryable;
    private final List<FieldErrorDetail> fieldErrors;

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
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.retryable = retryable;
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
}
