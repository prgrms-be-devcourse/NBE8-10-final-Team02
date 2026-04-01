package com.back.backend.domain.ai.usage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * AI provider 일간 사용량 저장소
 * 성공 요청과 rate limit hit는 UPSERT 네이티브 쿼리로 원자적으로 누적
 * (ON DUPLICATE KEY UPDATE로 race condition 없이 카운터 증가)
 */
public interface AiProviderUsageRepository extends JpaRepository<AiProviderUsage, Long> {

    /**
     * 특정 provider의 특정 날짜 사용량 조회
     * 해당 날짜 row가 없으면 빈 Optional 반환 → 호출 측에서 AiProviderUsage.empty() 사용
     */
    Optional<AiProviderUsage> findByProviderAndUsageDate(String provider, LocalDate date);

    /**
     * AI 호출 성공 시 사용량 UPSERT
     * row가 없으면 INSERT, 있으면 request_count+1 및 토큰 누적
     *
     * @param provider          AI provider 코드
     * @param date              사용 날짜 (UTC 기준)
     * @param promptTokens      입력 토큰 수
     * @param completionTokens  출력 토큰 수
     * @param totalTokens       전체 토큰 수
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ai_provider_usage (provider, usage_date, request_count, prompt_tokens, completion_tokens, total_tokens, rate_limit_hits)
            VALUES (:provider, :date, 1, :promptTokens, :completionTokens, :totalTokens, 0)
            ON CONFLICT (provider, usage_date) DO UPDATE SET
                request_count     = ai_provider_usage.request_count + 1,
                prompt_tokens     = ai_provider_usage.prompt_tokens + EXCLUDED.prompt_tokens,
                completion_tokens = ai_provider_usage.completion_tokens + EXCLUDED.completion_tokens,
                total_tokens      = ai_provider_usage.total_tokens + EXCLUDED.total_tokens
            """, nativeQuery = true)
    void upsertSuccess(
            @Param("provider") String provider,
            @Param("date") LocalDate date,
            @Param("promptTokens") long promptTokens,
            @Param("completionTokens") long completionTokens,
            @Param("totalTokens") long totalTokens
    );

    /**
     * AI 429 rate limit hit 시 카운터 UPSERT
     * row가 없으면 INSERT, 있으면 rate_limit_hits+1
     *
     * @param provider AI provider 코드
     * @param date     사용 날짜 (UTC 기준)
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ai_provider_usage (provider, usage_date, request_count, prompt_tokens, completion_tokens, total_tokens, rate_limit_hits)
            VALUES (:provider, :date, 0, 0, 0, 0, 1)
            ON CONFLICT (provider, usage_date) DO UPDATE SET
                rate_limit_hits = ai_provider_usage.rate_limit_hits + 1
            """, nativeQuery = true)
    void upsertRateLimitHit(
            @Param("provider") String provider,
            @Param("date") LocalDate date
    );
}
