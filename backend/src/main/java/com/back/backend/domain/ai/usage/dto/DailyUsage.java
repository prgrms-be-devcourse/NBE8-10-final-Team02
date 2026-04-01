package com.back.backend.domain.ai.usage.dto;

/**
 * 일간 요청 수 사용 현황 DTO
 *
 * @param used      오늘(UTC 기준) 사용한 요청 수
 * @param limit     일간 허용 최대 요청 수
 * @param resetsAt  다음 리셋 시각 (UTC 자정, ISO-8601 형식 예: "2026-04-02T00:00:00Z")
 */
public record DailyUsage(
        int used,
        int limit,
        String resetsAt
) {
}
