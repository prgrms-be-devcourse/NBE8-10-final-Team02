package com.back.backend.ai.client;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.springframework.http.HttpStatus;

/**
 * AI 클라이언트 호출 실패 시 발생하는 예외
 * ServiceException을 상속하므로 GlobalExceptionHandler에서 자동 처리
 * provider 정보를 포함하여 어떤 모델에서 실패했는지 로그로 추적
 */
public class AiClientException extends ServiceException {

    private final AiProvider provider;

    public AiClientException(AiProvider provider, ErrorCode errorCode, String message) {
        super(errorCode, HttpStatus.BAD_GATEWAY, message, true);
        this.provider = provider;
    }

    public AiClientException(AiProvider provider, ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, HttpStatus.BAD_GATEWAY, message, true, null);
        this.provider = provider;
        initCause(cause);
    }

    public AiProvider getProvider() {
        return provider;
    }
}
