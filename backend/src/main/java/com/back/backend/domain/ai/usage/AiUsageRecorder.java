package com.back.backend.domain.ai.usage;

import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.client.RateLimitType;
import com.back.backend.domain.ai.client.AiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * AI API 호출 결과를 인메모리 슬라이딩 윈도우(AiUsageStore)와 DB(AiProviderUsageRepository)에 기록
 * AiPipeline의 executeWithClient()에서 호출됨
 */
@Service
public class AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);

    private final AiUsageStore store;
    private final AiProviderUsageRepository repository;

    public AiUsageRecorder(AiUsageStore store, AiProviderUsageRepository repository) {
        this.store = store;
        this.repository = repository;
    }

    /**
     * AI 호출 성공 시 사용량 기록
     * 인메모리 슬라이딩 윈도우와 DB 모두에 기록
     *
     * @param provider   호출에 성공한 AI provider
     * @param tokenUsage 해당 호출의 토큰 사용량
     */
    public void recordSuccess(AiProvider provider, AiResponse.TokenUsage tokenUsage) {
        String code = provider.getValue();
        long total = tokenUsage != null ? tokenUsage.totalTokens() : 0L;

        // 인메모리 슬라이딩 윈도우에 기록 (RPM/TPM 실시간 추적용)
        store.recordRequest(code, total);

        // DB에 일간 누적 UPSERT
        try {
            repository.upsertSuccess(
                code,
                LocalDate.now(ZoneOffset.UTC),
                tokenUsage != null ? tokenUsage.promptTokens() : 0L,
                tokenUsage != null ? tokenUsage.completionTokens() : 0L,
                total
            );
        } catch (Exception e) {
            // 사용량 기록 실패가 서비스 흐름을 막지 않도록 로그만 남김
            log.error("[AiUsageRecorder] 성공 사용량 DB 기록 실패: provider={}, error={}", code, e.getMessage(), e);
        }
    }

    /**
     * AI 429 rate limit hit 시 카운터 기록
     * rateLimitType이 null이면 기록 skip (rate limit이 아닌 오류)
     *
     * @param provider      rate limit이 발생한 AI provider
     * @param rateLimitType MINUTE 또는 DAILY (null이면 skip)
     */
    public void recordRateLimitHit(AiProvider provider, RateLimitType rateLimitType) {
        // rate limit이 아닌 예외(빈 응답, 타임아웃 등)는 기록하지 않음
        if (rateLimitType == null) {
            return;
        }

        String code = provider.getValue();
        try {
            repository.upsertRateLimitHit(code, LocalDate.now(ZoneOffset.UTC));
        } catch (Exception e) {
            // 사용량 기록 실패가 서비스 흐름을 막지 않도록 로그만 남김
            log.error("[AiUsageRecorder] rate limit hit DB 기록 실패: provider={}, type={}, error={}",
                code, rateLimitType, e.getMessage(), e);
        }
    }
}
