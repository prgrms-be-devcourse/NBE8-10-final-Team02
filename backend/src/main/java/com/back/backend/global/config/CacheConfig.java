package com.back.backend.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 캐싱 설정
 * - @EnableCaching으로 @Cacheable, @CacheEvict 등 캐시 기능 활성화
 * - spring.cache.type=simple으로 로컬 ConcurrentMapCache 사용 (60초 TTL은 나중에 Caffeine 추가 시 구현 가능)
 * - AI 서비스 상태 조회(/api/v1/ai/status)의 DB 부하 감소 목적
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
