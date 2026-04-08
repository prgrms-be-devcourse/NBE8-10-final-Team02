package com.back.backend.domain.ai.usage;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.client.AiProvider;
import com.back.backend.domain.ai.usage.dto.AiStatusResponse;
import com.back.backend.domain.ai.usage.dto.ProviderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AiStatusService 통합 테스트
 * 4가지 시나리오 검증:
 * 1. 모두 정상 (available=true)
 * 2. 분당 한도 초과 (일간은 충분)
 * 3. 일간 한도 소진 (모두)
 * 4. 혼합 (한 provider 일간 소진, 다른 provider 분당 초과)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AI 서비스 가용성 상태 조회")
class AiStatusServiceTest {

    @Mock
    private AiClientRouter router;
    @Mock
    private AiUsageStore store;
    @Mock
    private AiProviderUsageRepository repository;
    @Mock
    private AiRateLimitProperties rateLimitProps;
    @Mock
    private AiClient defaultClient;
    @Mock
    private AiClient fallbackClient;

    private AiStatusService service;
    private AiRateLimitProperties.ProviderLimit geminiLimit;
    private AiRateLimitProperties.ProviderLimit groqLimit;

    @BeforeEach
    void setUp() {
        service = new AiStatusService(router, store, repository, rateLimitProps);

        // 한도 설정 초기화
        geminiLimit = mock(AiRateLimitProperties.ProviderLimit.class);
        groqLimit = mock(AiRateLimitProperties.ProviderLimit.class);

        // Gemini: RPM=10, RPD=250, TPM=250000, TPD=없음
        when(geminiLimit.getRpm()).thenReturn(10);
        when(geminiLimit.getRpd()).thenReturn(250);
        when(geminiLimit.getTpm()).thenReturn(250000);
        when(geminiLimit.hasTpd()).thenReturn(false);

        // Groq: RPM=30, RPD=1000, TPM=12000, TPD=100000
        when(groqLimit.getRpm()).thenReturn(30);
        when(groqLimit.getRpd()).thenReturn(1000);
        when(groqLimit.getTpm()).thenReturn(12000);
        when(groqLimit.hasTpd()).thenReturn(true);
        when(groqLimit.getTpd()).thenReturn(100000);

        // router 설정 — getAllClients()로 전체 provider 조회
        when(defaultClient.getProvider()).thenReturn(AiProvider.GEMINI);
        when(fallbackClient.getProvider()).thenReturn(AiProvider.GROQ);
        when(router.getAllClients()).thenReturn(List.of(defaultClient, fallbackClient));

        // rate limit 한도 조회
        when(rateLimitProps.getFor("gemini")).thenReturn(geminiLimit);
        when(rateLimitProps.getFor("groq")).thenReturn(groqLimit);
    }

    @Test
    @DisplayName("시나리오 1: 모두 정상 (available=true)")
    void testAllAvailable() {
        // given - RPM, TPM, 일간 사용량 모두 정상
        when(store.currentRpm("gemini")).thenReturn(3);  // 10 중 3
        when(store.currentTpm("gemini")).thenReturn(50000L);  // 250000 중 50000
        when(store.secondsUntilReset("gemini")).thenReturn(42);

        when(store.currentRpm("groq")).thenReturn(10);  // 30 중 10
        when(store.currentTpm("groq")).thenReturn(3000L);  // 12000 중 3000
        when(store.secondsUntilReset("groq")).thenReturn(58);

        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(50);  // 250 중 50
        when(geminiDaily.getTotalTokens()).thenReturn(100000L);

        AiProviderUsage groqDaily = mock(AiProviderUsage.class);
        when(groqDaily.getRequestCount()).thenReturn(200);  // 1000 중 200
        when(groqDaily.getTotalTokens()).thenReturn(30000L);  // 100000 중 30000

        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
        when(repository.findByProviderAndUsageDate("groq", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(groqDaily));

        // when
        AiStatusResponse response = service.getStatus();

        // then
        assertThat(response.available()).isTrue();
        assertThat(response.estimatedWaitSeconds()).isNull();
        assertThat(response.message()).isNull();
        assertThat(response.providers()).hasSize(2);

        // Gemini 상태 검증
        ProviderStatus gemini = response.providers().stream()
            .filter(p -> "gemini".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(gemini.status()).isEqualTo("available");
        assertThat(gemini.minuteUsage().used()).isEqualTo(3);
        assertThat(gemini.minuteUsage().percentage()).isEqualTo(30);  // 3/10 * 100 = 30
        assertThat(gemini.dailyUsage().used()).isEqualTo(50);
        assertThat(gemini.dailyUsage().percentage()).isEqualTo(20);  // 50/250 * 100 = 20
        assertThat(gemini.tokenUsage().minutePercentage()).isEqualTo(20);  // 50000/250000 * 100 = 20
        assertThat(gemini.tokenUsage().dailyPercentage()).isNull();  // Gemini TPD 미설정

        // Groq 상태 검증
        ProviderStatus groq = response.providers().stream()
            .filter(p -> "groq".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(groq.status()).isEqualTo("available");
        assertThat(groq.minuteUsage().percentage()).isEqualTo(33);  // 10/30 * 100 = 33
        assertThat(groq.dailyUsage().percentage()).isEqualTo(20);  // 200/1000 * 100 = 20
        assertThat(groq.tokenUsage().dailyPercentage()).isEqualTo(30);  // 30000/100000 * 100 = 30
    }

    @Test
    @DisplayName("시나리오 2: 분당 한도 초과 (일간은 충분)")
    void testMinuteRateLimited() {
        // given - RPM 초과
        when(store.currentRpm("gemini")).thenReturn(10);  // 정확히 한도
        when(store.currentTpm("gemini")).thenReturn(50000L);
        when(store.secondsUntilReset("gemini")).thenReturn(37);

        when(store.currentRpm("groq")).thenReturn(30);  // 정확히 한도, MINUTE_RATE_LIMITED
        when(store.currentTpm("groq")).thenReturn(3000L);
        when(store.secondsUntilReset("groq")).thenReturn(52);

        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(50);
        when(geminiDaily.getTotalTokens()).thenReturn(100000L);

        AiProviderUsage groqDaily = mock(AiProviderUsage.class);
        when(groqDaily.getRequestCount()).thenReturn(200);
        when(groqDaily.getTotalTokens()).thenReturn(30000L);

        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
        when(repository.findByProviderAndUsageDate("groq", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(groqDaily));

        // when
        AiStatusResponse response = service.getStatus();

        // then
        assertThat(response.available()).isFalse();
        assertThat(response.estimatedWaitSeconds()).isEqualTo(37);  // min(37, 52) = 37
        assertThat(response.message())
            .contains("잠시 과부하")
            .contains("37초");

        ProviderStatus gemini = response.providers().stream()
            .filter(p -> "gemini".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(gemini.status()).isEqualTo("minute_rate_limited");
        assertThat(gemini.minuteUsage().percentage()).isEqualTo(100);
    }

    @Test
    @DisplayName("시나리오 3: 일간 한도 소진")
    void testDailyExhausted() {
        // given - RPD 초과
        when(store.currentRpm("gemini")).thenReturn(0);
        when(store.currentTpm("gemini")).thenReturn(0L);
        when(store.secondsUntilReset("gemini")).thenReturn(0);

        when(store.currentRpm("groq")).thenReturn(0);
        when(store.currentTpm("groq")).thenReturn(0L);
        when(store.secondsUntilReset("groq")).thenReturn(0);

        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(250);  // 정확히 한도
        when(geminiDaily.getTotalTokens()).thenReturn(0L);

        AiProviderUsage groqDaily = mock(AiProviderUsage.class);
        when(groqDaily.getRequestCount()).thenReturn(1000);  // 정확히 한도
        when(groqDaily.getTotalTokens()).thenReturn(0L);

        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
        when(repository.findByProviderAndUsageDate("groq", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(groqDaily));

        // when
        AiStatusResponse response = service.getStatus();

        // then
        assertThat(response.available()).isFalse();
        assertThat(response.estimatedWaitSeconds()).isGreaterThan(0);  // UTC 자정까지의 초
        assertThat(response.message()).contains("오늘의 AI 서비스 사용량이 모두 소진");

        response.providers().forEach(p -> {
            assertThat(p.status()).isEqualTo("daily_exhausted");
            assertThat(p.dailyUsage().percentage()).isEqualTo(100);
        });
    }

    @Test
    @DisplayName("시나리오 4: 혼합 (한 provider 일간 소진, 다른 provider 분당 초과)")
    void testMixed() {
        // given - Gemini는 일간 소진, Groq는 분당 초과
        when(store.currentRpm("gemini")).thenReturn(0);
        when(store.currentTpm("gemini")).thenReturn(0L);
        when(store.secondsUntilReset("gemini")).thenReturn(0);

        when(store.currentRpm("groq")).thenReturn(30);  // 정확히 한도
        when(store.currentTpm("groq")).thenReturn(3000L);
        when(store.secondsUntilReset("groq")).thenReturn(45);

        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(250);  // 일간 소진
        when(geminiDaily.getTotalTokens()).thenReturn(0L);

        AiProviderUsage groqDaily = mock(AiProviderUsage.class);
        when(groqDaily.getRequestCount()).thenReturn(200);  // 충분함
        when(groqDaily.getTotalTokens()).thenReturn(30000L);

        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
        when(repository.findByProviderAndUsageDate("groq", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(groqDaily));

        // when
        AiStatusResponse response = service.getStatus();

        // then
        assertThat(response.available()).isFalse();
        assertThat(response.estimatedWaitSeconds()).isEqualTo(45);  // Groq의 resetInSeconds (더 짧음)
        assertThat(response.message()).contains("잠시 과부하");

        ProviderStatus gemini = response.providers().stream()
            .filter(p -> "gemini".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(gemini.status()).isEqualTo("daily_exhausted");

        ProviderStatus groq = response.providers().stream()
            .filter(p -> "groq".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(groq.status()).isEqualTo("minute_rate_limited");
    }

    @Test
    @DisplayName("Percentage 계산 정확성")
    void testPercentageCalculation() {
        // given
        when(store.currentRpm("gemini")).thenReturn(6);
        when(store.currentTpm("gemini")).thenReturn(42000L);
        when(store.secondsUntilReset("gemini")).thenReturn(23);

        when(store.currentRpm("groq")).thenReturn(1);
        when(store.currentTpm("groq")).thenReturn(1500L);
        when(store.secondsUntilReset("groq")).thenReturn(18);

        AiProviderUsage geminiDaily = mock(AiProviderUsage.class);
        when(geminiDaily.getRequestCount()).thenReturn(80);
        when(geminiDaily.getTotalTokens()).thenReturn(312000L);

        AiProviderUsage groqDaily = mock(AiProviderUsage.class);
        when(groqDaily.getRequestCount()).thenReturn(120);
        when(groqDaily.getTotalTokens()).thenReturn(8000L);

        when(repository.findByProviderAndUsageDate("gemini", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(geminiDaily));
        when(repository.findByProviderAndUsageDate("groq", LocalDate.now(ZoneOffset.UTC)))
            .thenReturn(Optional.of(groqDaily));

        // when
        AiStatusResponse response = service.getStatus();

        // then - 예시 응답과 비교
        ProviderStatus gemini = response.providers().stream()
            .filter(p -> "gemini".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(gemini.minuteUsage().percentage()).isEqualTo(60);  // 6/10 * 100
        assertThat(gemini.dailyUsage().percentage()).isEqualTo(32);   // 80/250 * 100
        assertThat(gemini.tokenUsage().minutePercentage()).isEqualTo(17);  // 42000/250000 * 100

        ProviderStatus groq = response.providers().stream()
            .filter(p -> "groq".equals(p.name()))
            .findFirst()
            .orElseThrow();
        assertThat(groq.dailyUsage().percentage()).isEqualTo(12);  // 120/1000 * 100
        assertThat(groq.tokenUsage().dailyPercentage()).isEqualTo(8);  // 8000/100000 * 100
    }
}
