package com.back.backend.domain.ai.batch;

/**
 * Vertex AI (Gemini 2.5 Flash) 배치 전략.
 *
 * <ul>
 *   <li>최대 출력: 32,000 토큰 (Long Output 모델 활용)</li>
 *   <li>최대 repo: 8개</li>
 *   <li>입력 예산: 800,000 chars (8 repos × 100,000 chars/repo ≈ 25K tokens/repo)</li>
 *   <li>언어: 영어 강제 — 프롬프트에서 English-only 지시</li>
 * </ul>
 *
 * <p>입력 예산 근거:
 * <ul>
 *   <li>code_structure 고정 상한: 40,000 chars</li>
 *   <li>overview(README) 상한: 8,000 chars</li>
 *   <li>diff 예산: 52,000 chars = Early/Mid/Late 각 ~17,000</li>
 *   <li>합계: ~100,000 chars/repo → 8 repos = 800,000 total</li>
 *   <li>Vertex AI 컨텍스트 윈도우: 1M tokens = 4M chars → 800K chars 충분히 수용</li>
 * </ul>
 */
public class VertexAiStrategy implements BatchProviderStrategy {

    @Override
    public int getMaxReposPerCall() {
        return 8;
    }

    @Override
    public int getMaxOutputTokens() {
        return 32_000;
    }

    @Override
    public int getGlobalBudgetChars() {
        // 8 repos × 100,000 chars/repo = 800,000 총 입력 예산
        // overview(8K) + code_structure(40K) + diffs(52K) ≈ 100K/repo
        return 800_000;
    }

}
