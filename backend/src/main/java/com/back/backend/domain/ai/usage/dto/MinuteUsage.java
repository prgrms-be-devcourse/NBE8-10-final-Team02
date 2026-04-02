package com.back.backend.domain.ai.usage.dto;

/**
 * 분당 요청 수 사용 현황 DTO
 *
 * @param used            현재 분당 사용 요청 수 (슬라이딩 윈도우 기준)
 * @param limit           분당 허용 최대 요청 수
 * @param percentage      사용률 퍼센트 (0-100, 반올림)
 * @param resetInSeconds  윈도우 초기화까지 남은 초
 */
public record MinuteUsage(
        int used,
        int limit,
        int percentage,
        int resetInSeconds
) {
}
