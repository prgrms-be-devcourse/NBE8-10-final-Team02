package com.back.backend.domain.ai.client;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

/**
 * AI 클라이언트 호출 실패 시 발생하는 예외
 * ServiceException을 상속하므로 GlobalExceptionHandler에서 자동 처리
 * provider 정보를 포함하여 어떤 모델에서 실패했는지 로그로 추적
 * rateLimitType: rate limit 429 응답인 경우 MINUTE/DAILY 구분 (그 외 null)
 */
public class AiClientException extends ServiceException {

    private final AiProvider provider;
    /** rate limit 종류. rate limit이 아닌 예외(빈 응답, 타임아웃 등)에서는 null */
    private final RateLimitType rateLimitType;

    /** 기존 생성자 — rateLimitType=null, retryAfterSeconds=null, status=BAD_GATEWAY 고정 */
    public AiClientException(AiProvider provider, ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.BAD_GATEWAY, message, true);
        this.provider = provider;
        this.rateLimitType = null;
    }

    /** 기존 생성자 — cause 포함, rateLimitType=null */
    public AiClientException(AiProvider provider, ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, HttpStatus.BAD_GATEWAY, message, true, null);
        this.provider = provider;
        this.rateLimitType = null;
        initCause(cause);
    }

    /**
     * rate limit 전용 생성자 — MINUTE/DAILY 구분 및 재시도 대기 시간 포함
     * DAILY 한도 소진 시 retryable=false, MINUTE 초과 시 retryable=true
     *
     * @param provider          오류가 발생한 AI provider
     * @param errorCode         AI_DAILY_LIMIT_EXCEEDED 또는 EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE
     * @param status            HTTP 응답 상태 코드 (503 또는 502)
     * @param message           사용자에게 전달할 메시지
     * @param rateLimitType     MINUTE 또는 DAILY
     * @param retryAfterSeconds 재시도까지 기다려야 하는 초
     * @param cause             원인 예외
     */
    public AiClientException(
            AiProvider provider,
            ErrorCode errorCode,
            HttpStatus status,
            String message,
            RateLimitType rateLimitType,
            int retryAfterSeconds,
            Throwable cause
    ) {
        // DAILY 한도 소진은 retryable=false, MINUTE 초과는 retryable=true
        super(errorCode, status, message, errorCode != ErrorCode.AI_DAILY_LIMIT_EXCEEDED, retryAfterSeconds, null);
        this.provider = provider;
        this.rateLimitType = rateLimitType;
        if (cause != null) {
            initCause(cause);
        }
    }

    public AiProvider getProvider() {
        return provider;
    }

    /** rate limit 종류. rate limit 예외가 아니면 null 반환. */
    public RateLimitType getRateLimitType() {
        return rateLimitType;
    }
}
