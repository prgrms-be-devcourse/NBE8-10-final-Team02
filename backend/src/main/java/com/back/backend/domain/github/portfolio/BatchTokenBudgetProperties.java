package com.back.backend.domain.github.portfolio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Batch AI 호출 1회에 허용되는 전체 토큰 예산 설정.
 *
 * <p>application.yml의 {@code ai.portfolio.batch} 블록으로 설정한다.
 *
 * <pre>
 * ai:
 *   portfolio:
 *     batch:
 *       global-budget-chars: 480000   # Gemini free 기본값 (120K tokens × 4 chars)
 *       # Groq free (TPM 빡빡): 120000으로 낮출 것 (30K tokens × 4 chars)
 * </pre>
 *
 * <p>1 token ≈ 4 chars (영문 기준). 한글이 많으면 token 소비가 더 크므로
 * 실제 운영 환경에서 모니터링 후 값을 조정한다.
 */
@ConfigurationProperties(prefix = "ai.portfolio.batch")
public class BatchTokenBudgetProperties {

    /**
     * Batch 호출 1회 전체 문자 예산.
     * 기본값: 480_000 chars ≈ 120K tokens (Gemini free tier 기준)
     */
    private int globalBudgetChars = 480_000;

    public int getGlobalBudgetChars() {
        return globalBudgetChars;
    }

    public void setGlobalBudgetChars(int globalBudgetChars) {
        this.globalBudgetChars = globalBudgetChars;
    }
}
