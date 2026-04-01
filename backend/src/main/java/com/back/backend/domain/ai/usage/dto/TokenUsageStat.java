package com.back.backend.domain.ai.usage.dto;

/**
 * 토큰 사용량 통계 DTO
 *
 * @param minuteUsed       현재 분당 사용 토큰 수 (슬라이딩 윈도우 기준)
 * @param minuteLimit      분당 허용 최대 토큰 수
 * @param minutePercentage 분당 사용률 퍼센트 (0-100, 반올림)
 * @param dailyUsed        오늘(UTC) 총 사용 토큰 수
 * @param dailyLimit       일간 허용 최대 토큰 수. null이면 한도 미설정 (체크 비활성화)
 * @param dailyPercentage  일간 사용률 퍼센트 (0-100, 반올림). dailyLimit이 null이면 null
 */
public record TokenUsageStat(
        long minuteUsed,
        long minuteLimit,
        int minutePercentage,
        long dailyUsed,
        Long dailyLimit,
        Integer dailyPercentage
) {
}
