package com.back.backend.global.exception;

import com.back.backend.global.response.ApiErrorResponse;
import com.back.backend.global.response.FieldErrorDetail;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceException(ServiceException exception) {
        return errorResponse(
                exception.getStatus(),
                exception.getErrorCode(),
                exception.getMessage(),
                exception.isRetryable(),
                exception.getFieldErrors(),
                exception
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        return validationErrorResponse(extractFieldErrors(exception));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception) {
        return validationErrorResponse(extractFieldErrors(exception));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
        List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorDetail(lastPathSegment(violation.getPropertyPath().toString()), violation.getMessage()))
                .toList();

        return validationErrorResponse(fieldErrors);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiErrorResponse> handleRequestValidationException(Exception exception) {
        List<FieldErrorDetail> fieldErrors = null;

        if (exception instanceof MethodArgumentTypeMismatchException typeMismatchException) {
            fieldErrors = List.of(new FieldErrorDetail(typeMismatchException.getName(), "invalid"));
        } else if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            fieldErrors = List.of(new FieldErrorDetail(missingParameterException.getParameterName(), "required"));
        }

        return validationErrorResponse(fieldErrors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception) {
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
                false,
                null,
                exception
        );
    }

    private ResponseEntity<ApiErrorResponse> validationErrorResponse(List<FieldErrorDetail> fieldErrors) {
        return errorResponse(
                HttpStatus.BAD_REQUEST,
                ErrorCode.REQUEST_VALIDATION_FAILED,
                "요청 값을 다시 확인해주세요.",
                false,
                fieldErrors,
                null
        );
    }

    private ResponseEntity<ApiErrorResponse> errorResponse(
            HttpStatus status,
            ErrorCode errorCode,
            String message,
            boolean retryable,
            List<FieldErrorDetail> fieldErrors,
            Exception exception
    ) {
        if (status.is5xxServerError()) {
            log.error("Unhandled errorCode={}, status={}", errorCode.name(), status.value(), exception);
        } else {
            log.warn("Handled errorCode={}, status={}, message={}", errorCode.name(), status.value(), message);
        }

        return ResponseEntity
                .status(status)
                .body(ApiErrorResponse.of(errorCode, message, retryable, fieldErrors));
    }

    private List<FieldErrorDetail> extractFieldErrors(BindException exception) {
        List<FieldErrorDetail> fieldErrors = new ArrayList<>();

        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new FieldErrorDetail(fieldError.getField(), reason(fieldError.getDefaultMessage())));
        }

        return fieldErrors;
    }

    private String reason(String defaultMessage) {
        if (defaultMessage == null || defaultMessage.isBlank()) {
            return "invalid";
        }

        return defaultMessage;
    }

    private String lastPathSegment(String path) {
        int lastDotIndex = path.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == path.length() - 1) {
            return path;
        }

        return path.substring(lastDotIndex + 1);
    }
}
