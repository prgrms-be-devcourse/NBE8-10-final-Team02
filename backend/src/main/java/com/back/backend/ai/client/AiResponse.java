package com.back.backend.ai.client;

/**
 * AI 모델 호출 공통 응답 DTO.
 * 모든 provider 구현체가 자신의 응답을 이 형식으로 변환하여 반환
 *
 * @param content    AI가 생성한 텍스트 (JSON 문자열)
 * @param tokenUsage 토큰 사용량 — 비용 추적 및 상한 체크에 사용
 */
public record AiResponse(
    String content,
    TokenUsage tokenUsage
) {
    /**
     * @param promptTokens     입력 토큰 수
     * @param completionTokens 출력 토큰 수
     * @param totalTokens      합계
     */
    public record TokenUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
    ) {
    }
}
