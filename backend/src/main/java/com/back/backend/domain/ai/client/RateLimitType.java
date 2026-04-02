package com.back.backend.domain.ai.client;

/**
 * AI provider 429 응답의 rate limit 종류
 * MINUTE: 분당 한도 초과 (RPM/TPM) — 잠깐 기다리면 해결됨 (보통 60초 이내)
 * DAILY:  일간 한도 소진 (RPD/TPD) — 내일 UTC 00:00 (KST 09:00) 이후 해결됨
 */
public enum RateLimitType {
    MINUTE,
    DAILY
}
