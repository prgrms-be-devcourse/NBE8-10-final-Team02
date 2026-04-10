package com.back.backend.domain.ai.batch;

/**
 * Gemini 무료 티어 배치 전략.
 *
 * <ul>
 *   <li>최대 출력: 8,000 토큰 (Gemini free tier 한도)</li>
 *   <li>최대 repo: 2개</li>
 *   <li>입력 예산: 480,000 chars (2 repos × 240,000 chars/repo ≈ 60K tokens/repo)</li>
 *   <li>언어: 영어 강제 — 프롬프트에서 English-only 지시</li>
 * </ul>
 */
public class GeminiFreeStrategy implements BatchProviderStrategy {

    @Override
    public int getMaxReposPerCall() {
        return 2;
    }

    @Override
    public int getMaxOutputTokens() {
        return 8_000;
    }

    @Override
    public int getGlobalBudgetChars() {
        // 2 repos × 240,000 chars/repo = 480,000 총 입력 예산
        // overview(8K) + code_structure(40K) + diffs(192K) ≈ 240K/repo — 여유 있음
        return 480_000;
    }

}
