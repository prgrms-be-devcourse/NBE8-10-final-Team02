package com.back.backend.domain.ai.usage;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AI provider 일간 사용량 누적 엔티티
 * provider + usage_date 조합이 유니크 (uq_provider_date)
 * UPSERT는 네이티브 쿼리로 처리하므로 JPA save()는 주로 조회용으로 활용
 */
@Entity
@Table(name = "ai_provider_usage")
public class AiProviderUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** AI provider 코드 (예: "gemini", "groq") */
    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    /** 사용 날짜 (UTC 기준) */
    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    /** 성공 요청 수 */
    @Column(name = "request_count", nullable = false)
    private int requestCount;

    /** 입력 토큰 누적 */
    @Column(name = "prompt_tokens", nullable = false)
    private long promptTokens;

    /** 출력 토큰 누적 */
    @Column(name = "completion_tokens", nullable = false)
    private long completionTokens;

    /** 전체 토큰 누적 (prompt + completion) */
    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    /** 429 rate limit 발생 횟수 */
    @Column(name = "rate_limit_hits", nullable = false)
    private int rateLimitHits;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected AiProviderUsage() {
        // JPA 기본 생성자
    }

    private AiProviderUsage(String provider, LocalDate usageDate,
                             int requestCount, long promptTokens, long completionTokens,
                             long totalTokens, int rateLimitHits) {
        this.provider = provider;
        this.usageDate = usageDate;
        this.requestCount = requestCount;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.rateLimitHits = rateLimitHits;
    }

    /**
     * 해당 날짜에 row가 없을 때 기본값을 반환하기 위한 팩토리 메서드
     * requestCount=0, 모든 토큰 카운트=0으로 초기화된 인스턴스를 반환
     *
     * @param provider AI provider 코드
     * @return 사용량이 0인 빈 AiProviderUsage (DB에 저장되지 않음)
     */
    public static AiProviderUsage empty(String provider) {
        return new AiProviderUsage(provider, LocalDate.now(), 0, 0L, 0L, 0L, 0);
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public LocalDate getUsageDate() {
        return usageDate;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public long getPromptTokens() {
        return promptTokens;
    }

    public long getCompletionTokens() {
        return completionTokens;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public int getRateLimitHits() {
        return rateLimitHits;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
