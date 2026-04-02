package com.back.backend.domain.ai.usage.dto;

/**
 * 개별 AI provider의 상태 DTO
 *
 * @param name        AI provider 코드 (예: "gemini", "groq")
 * @param status      가용성 상태를 snake_case 소문자 문자열로 직렬화
 *                    ("available", "minute_rate_limited", "daily_exhausted", "unavailable")
 * @param minuteUsage 분당 요청 수 현황
 * @param dailyUsage  일간 요청 수 현황
 * @param tokenUsage  토큰 사용량 현황
 */
public record ProviderStatus(
        String name,
        String status,
        MinuteUsage minuteUsage,
        DailyUsage dailyUsage,
        TokenUsageStat tokenUsage
) {

    /**
     * ProviderAvailability enum을 snake_case 소문자 문자열로 변환하는 팩토리 메서드
     * 예: MINUTE_RATE_LIMITED → "minute_rate_limited"
     */
    public static ProviderStatus of(
            String name,
            ProviderAvailability availability,
            MinuteUsage minuteUsage,
            DailyUsage dailyUsage,
            TokenUsageStat tokenUsage
    ) {
        // enum.name()은 이미 UPPER_SNAKE_CASE이므로 toLowerCase()만으로 snake_case 소문자 변환
        String statusStr = availability.name().toLowerCase();
        return new ProviderStatus(name, statusStr, minuteUsage, dailyUsage, tokenUsage);
    }
}
