package com.back.backend.domain.ai.batch;

/**
 * Vertex AI (Gemini 2.5 Flash) 배치 전략.
 *
 * <ul>
 *   <li>최대 출력: 32,000 토큰 (Long Output 모델 활용 — 실제 한도 최대 65,535)</li>
 *   <li>최대 repo: 16개 (한국어 16개 repo JSON ≈ 20,000~28,000 토큰 예상)</li>
 *   <li>언어: 한국어 허용 — 기본 프롬프트 지시를 그대로 따름</li>
 * </ul>
 */
public class VertexAiStrategy implements BatchProviderStrategy {

    @Override
    public int getMaxReposPerCall() {
        return 16;
    }

    @Override
    public int getMaxOutputTokens() {
        return 64_000;
    }

    @Override
    public boolean isEnglishOnly() {
        return true;
    }
}
