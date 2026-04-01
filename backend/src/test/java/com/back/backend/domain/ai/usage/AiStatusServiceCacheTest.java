package com.back.backend.domain.ai.usage;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.usage.dto.AiStatusResponse;
import com.back.backend.support.ApiTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AiStatusService 캐싱 기능 통합 테스트
 * - Caffeine 캐시 동작 검증 (캐시 히트 시 repository 미호출)
 * - 테스트 간 캐시 격리: @BeforeEach에서 캐시 명시적 초기화
 *
 * AiClientRouter, AiProviderUsageRepository를 @MockitoBean으로 대체
 * → 실제 DB/외부 API 호출 없이 캐시 동작만 순수 검증
 */
// 이 테스트는 Spring 컨텍스트의 CacheManager(Caffeine)와 @MockitoBean repository를 공유한다.
// 병렬 실행 시 cacheManager.clear()와 reset(repository)가 다른 테스트 메서드 실행 중에
// 끼어들어 검증 결과를 오염시킬 수 있으므로 반드시 같은 스레드에서 순차 실행해야 한다.
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("AiStatusService 캐싱")
class AiStatusServiceCacheTest extends ApiTestBase {

    @MockitoBean
    private AiClientRouter router;

    @MockitoBean
    private AiProviderUsageRepository repository;

    @Autowired
    private AiStatusService aiStatusService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUpMocks() {
        // 테스트 간 캐시 격리
        cacheManager.getCache("aiStatus").clear();

        // router: gemini를 default로, fallback 없음
        // AiClient는 Spring bean이 2개(geminiClient, groqClient)라 @MockitoBean 불가 → 로컬 mock 사용
        AiClient defaultClient = mock(AiClient.class);
        when(defaultClient.getProvider()).thenReturn(AiProvider.GEMINI);
        when(router.getDefault()).thenReturn(defaultClient);
        when(router.getFallback()).thenReturn(Optional.empty());

        // repository: 사용량 없음 (캐시 동작 검증에만 집중)
        when(repository.findByProviderAndUsageDate(any(), any()))
            .thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("첫 번째 호출: DB 조회 발생")
    void firstCall_dbQueried() {
        AiStatusResponse response = aiStatusService.getStatus();

        assertThat(response.available()).isTrue();
        verify(repository, atLeastOnce())
            .findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("두 번째 호출: 캐시 히트, DB 추가 조회 없음")
    void secondCall_cacheHit_noExtraDbQuery() {
        aiStatusService.getStatus();
        reset(repository);  // 첫 조회 카운트 제거

        AiStatusResponse response = aiStatusService.getStatus();

        assertThat(response).isNotNull();
        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("10회 연속 호출: DB 조회 1회만")
    void multipleCallsInCache_dbQueriedOnce() {
        for (int i = 0; i < 10; i++) {
            aiStatusService.getStatus();
        }

        verify(repository, times(1))
            .findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("캐시 초기화 후 재조회: DB 재조회 발생")
    void afterCacheClear_dbQueriedAgain() {
        aiStatusService.getStatus();
        verify(repository, times(1))
            .findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));

        cacheManager.getCache("aiStatus").clear();
        aiStatusService.getStatus();

        verify(repository, times(2))
            .findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @DisplayName("캐시 격리: 이전 테스트 캐시 상태 영향 없음")
    void cacheIsolation_noLeakBetweenTests() {
        aiStatusService.getStatus();

        verify(repository, times(1))
            .findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC));
    }
}
