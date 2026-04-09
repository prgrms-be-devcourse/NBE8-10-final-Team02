package com.back.backend.domain.ai.batch;

/**
 * Gemini 무료 티어 배치 전략.
 *
 * <ul>
 *   <li>최대 출력: 8,192 토큰 (Gemini free tier 고정 한도)</li>
 *   <li>최대 repo: 2개 (한국어 2개 repo JSON ≈ 6,000~7,000 토큰 예상)</li>
 *   <li>언어: 영어 강제 — 한국어 대비 2~3배 토큰 절약</li>
 * </ul>
 */
public class GeminiFreeStrategy implements BatchProviderStrategy {

    @Override
    public int getMaxReposPerCall() {
        return 2;
    }

    @Override
    public int getMaxOutputTokens() {
        return 8_192;
    }

    @Override
    public boolean isEnglishOnly() {
        return true;
    }
}
