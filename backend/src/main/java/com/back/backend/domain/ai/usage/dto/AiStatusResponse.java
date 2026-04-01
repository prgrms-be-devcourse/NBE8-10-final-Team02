package com.back.backend.domain.ai.usage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * GET /api/v1/ai/status 응답 DTO
 * available이 false일 때만 estimatedWaitSeconds, message가 채워짐
 * null 필드는 JSON 직렬화 시 제외
 *
 * @param available            하나 이상의 provider가 AVAILABLE 상태이면 true
 * @param estimatedWaitSeconds 모든 provider가 rate limited일 때 예상 대기 초 (null이면 제외)
 * @param message              비가용 상태일 때 사용자에게 표시할 메시지 (null이면 제외)
 * @param providers            각 provider의 상세 상태 목록
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiStatusResponse(
        boolean available,
        Integer estimatedWaitSeconds,
        String message,
        List<ProviderStatus> providers
) {
}
