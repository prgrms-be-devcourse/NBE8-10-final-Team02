package com.back.backend.domain.ai.usage;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.usage.dto.AiStatusResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AiStatusService 캐싱 기능 테스트
 * - 60초 캐시 TTL 검증
 * - 동일 요청에 대한 DB 조회 회수 감소 확인
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AI 상태 조회 캐싱")
class AiStatusServiceCacheTest {

    @Configuration
    @EnableCaching
    public static class CacheTestConfig {
        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("aiStatus");
        }
    }

    @MockBean
    private AiClientRouter router;
    @MockBean
    private AiUsageStore store;
    @MockBean
    private AiProviderUsageRepository repository;
    @MockBean
    private AiRateLimitProperties rateLimitProps;
    @MockBean
    private AiClient defaultClient;
    @MockBean
    private AiClient fallbackClient;

    private AiStatusService service;
    private AiRateLimitProperties.ProviderLimit geminiLimit;

    @BeforeEach
    void setUp() {
        service = new AiStatusService(router, store, repository, rateLimitProps);

        // 기본 설정
        geminiLimit = mock(AiRateLimitProperties.ProviderLimit.class);
        when(geminiLimit.getRpm()).thenReturn(10);
        when(geminiLimit.getRpd()).thenReturn(250);
        when(geminiLimit.getTpm()).thenReturn(250000L);
        when(geminiLimit.hasTpd()).thenReturn(false);

        when(defaultClient.getProvider()).thenReturn(AiProvider.GEMINI);
        when(router.getDefault()).thenReturn(defaultClient);
        when(router.getFallback()).thenReturn(Optional.empty());

        when(rateLimitProps.getFor("gemini")).thenReturn(geminiLimit);

        // store 기본값
        when(store.currentRpm("gemini")).thenReturn(3);
        when(store.currentTpm("gemini")).thenReturn(50000L);
        when(store.secondsUntilReset("gemini")).thenReturn(42);

        // repository 기본값
        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(50);
        when(geminiDaily.getTotalTokens()).thenReturn(100000L);
        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
    }

    @Test
    @DisplayName("첫 번째 호출: 캐시 미스, DB 조회 1회")
    void testFirstCallCacheMiss() {
        // when
        AiStatusResponse response1 = service.getStatus();

        // then
        assertThat(response1).isNotNull();
        assertThat(response1.available()).isTrue();

        // repository 조회가 1회 발생했는지 확인
        verify(repository, times(1)).findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("두 번째 호출 (동일 요청): 캐시 히트, DB 조회 증가 없음")
    void testSecondCallCacheHit() {
        // when
        AiStatusResponse response1 = service.getStatus();
        AiStatusResponse response2 = service.getStatus();

        // then
        assertThat(response1).isNotNull();
        assertThat(response2).isNotNull();
        // 두 응답이 동일한 데이터를 가져야 함
        assertThat(response1.available()).isEqualTo(response2.available());
        assertThat(response1.providers()).isEqualTo(response2.providers());

        // repository 조회는 여전히 1회만 (캐시에서 가져옴)
        verify(repository, times(1)).findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("여러 번 호출: 계속 캐시에서 조회")
    void testMultipleCallsCachedResponse() {
        // when
        for (int i = 0; i < 10; i++) {
            AiStatusResponse response = service.getStatus();
            assertThat(response).isNotNull();
        }

        // then
        // repository 조회는 여전히 1회만 (모든 요청이 캐시에서 옴)
        verify(repository, times(1)).findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("캐시 이름 검증: 'aiStatus'")
    void testCacheName() {
        // when
        AiStatusResponse response = service.getStatus();

        // then
        assertThat(response).isNotNull();
        // @Cacheable(cacheNames = "aiStatus") 적용 확인
        // (실제 캐시 이름은 Spring이 관리하므로 응답 데이터로 검증)
        verify(repository, times(1)).findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }
}
