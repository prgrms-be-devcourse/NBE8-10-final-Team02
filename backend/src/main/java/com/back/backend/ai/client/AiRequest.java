package com.back.backend.ai.client;

/**
 * AI 모델 호출 공통 요청 DTO.
 * provider에 무관하게 동일한 구조로 요청을 구성
 * 각 provider 구현체가 이 DTO를 자신의 API 형식으로 변환
 *
 * @param systemPrompt    시스템 프롬프트 — AI의 역할과 공통 규칙 정의
 * @param developerPrompt 개발자 프롬프트 — 기능별 세부 지시와 출력 규칙
 * @param userMessage     사용자 메시지 — 실제 입력 데이터 (JSON payload)
 * @param temperature     생성 다양성 (0.0~1.0). 낮을수록 결정적
 * @param maxTokens       최대 출력 토큰 수
 */
public record AiRequest(
    String systemPrompt,
    String developerPrompt,
    String userMessage,
    double temperature,
    int maxTokens
) {
}
