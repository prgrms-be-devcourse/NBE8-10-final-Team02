package com.back.backend.domain.ai.usage.dto;

/**
 * AI provider 가용성 상태
 * AVAILABLE           — 정상 사용 가능
 * MINUTE_RATE_LIMITED — 분당 한도 초과 (RPM 또는 TPM), 잠시 후 해결
 * DAILY_EXHAUSTED     — 일간 한도 소진 (RPD 또는 TPD), 내일 UTC 자정에 해결
 * UNAVAILABLE         — 설정 오류 등 기타 비가용 상태
 */
public enum ProviderAvailability {
    AVAILABLE,
    MINUTE_RATE_LIMITED,
    DAILY_EXHAUSTED,
    UNAVAILABLE
}
