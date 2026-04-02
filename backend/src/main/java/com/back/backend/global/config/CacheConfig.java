package com.back.backend.global.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 캐싱 설정
 * - @EnableCaching으로 @Cacheable, @CacheEvict 등 캐시 기능 활성화
 * - Caffeine 캐시 사용: 메모리 효율적, 높은 동시성 지원
 * - AI 서비스 상태 조회(/api/v1/ai/status)의 DB 부하 감소 목적
 *
 * 캐시 구성:
 * - expireAfterWrite: 5초 TTL (마지막 쓰기 이후 5초 경과하면 만료)
 * - initialCapacity: 16 (초기 할당 크기)
 * - maximumSize: 100 (최대 캐시 항목 수)
 * - recordStats: 캐시 통계 기록 (모니터링용)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Caffeine 기반 CacheManager 구성
     * 5초 TTL로 설정하여 폴링 주기와 맞춤
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("aiStatus");
        cacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(5))
                .initialCapacity(16)
                .maximumSize(100)
                .recordStats()
        );
        return cacheManager;
    }
}
