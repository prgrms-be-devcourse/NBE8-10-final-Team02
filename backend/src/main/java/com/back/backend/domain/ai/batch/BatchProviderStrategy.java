package com.back.backend.domain.ai.batch;

/**
 * AI Provider별 배치 호출 설정 전략.
 *
 * <p>각 provider의 출력 토큰 한도와 언어 제약이 다르므로,
 * provider에 따라 배치 크기·maxTokens·언어를 캡슐화한다.
 *
 * <ul>
 *   <li>{@link GeminiFreeStrategy} — Gemini 무료 티어 (8,192 토큰, 영어 강제)</li>
 *   <li>{@link VertexAiStrategy}   — Vertex AI (32,000 토큰, 한국어 허용)</li>
 * </ul>
 */
public interface BatchProviderStrategy {

    /** 1회 AI 호출당 처리 가능한 최대 repo 수 */
    int getMaxReposPerCall();

    /** AI 응답 최대 출력 토큰 수 */
    int getMaxOutputTokens();

    /**
     * true이면 프롬프트에 "Only English" 지시를 추가한다.
     * Gemini free tier는 영어 출력이 토큰 효율이 2~3배 높다.
     */
    boolean isEnglishOnly();
}
