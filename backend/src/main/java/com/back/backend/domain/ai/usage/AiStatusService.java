package com.back.backend.domain.ai.usage;

import com.back.backend.domain.ai.client.AiClient;
import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.usage.dto.*;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AI provider 가용성 상태 조회 서비스
 * 인메모리 슬라이딩 윈도우(AiUsageStore)와 DB(AiProviderUsageRepository) 데이터를 합산하여
 * 각 provider의 AVAILABLE/MINUTE_RATE_LIMITED/DAILY_EXHAUSTED 상태를 판정
 */
@Service
public class AiStatusService {

    private final AiClientRouter router;
    private final AiUsageStore store;
    private final AiProviderUsageRepository repository;
    private final AiRateLimitProperties rateLimitProps;

    public AiStatusService(
            AiClientRouter router,
            AiUsageStore store,
            AiProviderUsageRepository repository,
            AiRateLimitProperties rateLimitProps
    ) {
        this.router = router;
        this.store = store;
        this.repository = repository;
        this.rateLimitProps = rateLimitProps;
    }

    /**
     * 설정된 default + fallback provider에 대한 가용성 상태를 조회하여 반환
     *
     * @return AI 서비스 전체 가용성 및 각 provider 상태
     */
    public AiStatusResponse getStatus() {
        // default + fallback provider 목록 구성 (중복 제거)
        List<AiClient> clients = new ArrayList<>();
        clients.add(router.getDefault());
        router.getFallback().ifPresent(fb -> {
            if (!fb.getProvider().equals(router.getDefault().getProvider())) {
                clients.add(fb);
            }
        });

        // 각 provider 상태 빌드
        List<ProviderStatus> providerStatuses = clients.stream()
            .map(this::buildProviderStatus)
            .toList();

        // 하나라도 AVAILABLE이면 전체 available=true
        boolean anyAvailable = providerStatuses.stream()
            .anyMatch(ps -> "available".equals(ps.status()));

        Integer waitSeconds = anyAvailable ? null : estimateWaitSeconds(providerStatuses);
        String message = buildMessage(providerStatuses, waitSeconds);

        return new AiStatusResponse(anyAvailable, waitSeconds, message, providerStatuses);
    }

    /**
     * 특정 AiClient의 상태를 빌드
     * 1. 인메모리에서 RPM/TPM 및 윈도우 초기화 시간 조회
     * 2. DB에서 오늘(UTC) 일간 사용량 조회
     * 3. 한도 설정 조회 후 상태 판정
     */
    private ProviderStatus buildProviderStatus(AiClient client) {
        String code = client.getProvider().getValue();
        AiRateLimitProperties.ProviderLimit limits = rateLimitProps.getFor(code);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        // 인메모리 슬라이딩 윈도우 데이터
        int currentRpm = store.currentRpm(code);
        long currentTpm = store.currentTpm(code);
        int resetInSeconds = store.secondsUntilReset(code);

        // DB에서 일간 사용량 조회 (없으면 empty 사용)
        AiProviderUsage daily = repository.findByProviderAndUsageDate(code, today)
            .orElse(AiProviderUsage.empty(code));

        // 상태 판정: DAILY 먼저 체크 (우선순위 높음)
        ProviderAvailability availability;
        if (daily.getRequestCount() >= limits.getRpd()
                || (limits.hasTpd() && daily.getTotalTokens() >= limits.getTpd())) {
            // 일간 요청 수 또는 일간 토큰 한도 초과 → 오늘은 더 이상 불가
            availability = ProviderAvailability.DAILY_EXHAUSTED;
        } else if (currentRpm >= limits.getRpm() || currentTpm >= limits.getTpm()) {
            // 분당 요청 수 또는 분당 토큰 한도 초과 → 잠시 후 가능
            availability = ProviderAvailability.MINUTE_RATE_LIMITED;
        } else {
            availability = ProviderAvailability.AVAILABLE;
        }

        // UTC 자정 시간 계산 (일간 리셋 시각)
        String resetsAt = todayUtcMidnightIso();

        MinuteUsage minuteUsage = new MinuteUsage(currentRpm, limits.getRpm(), resetInSeconds);
        DailyUsage dailyUsage = new DailyUsage(daily.getRequestCount(), limits.getRpd(), resetsAt);
        TokenUsageStat tokenUsage = new TokenUsageStat(
            currentTpm,
            limits.getTpm(),
            daily.getTotalTokens(),
            limits.hasTpd() ? limits.getTpd().longValue() : null
        );

        return ProviderStatus.of(code, availability, minuteUsage, dailyUsage, tokenUsage);
    }

    /**
     * 모든 provider가 rate limited일 때 예상 대기 초를 계산
     * - 모두 DAILY_EXHAUSTED: UTC 자정까지 남은 초
     * - 모두 MINUTE_RATE_LIMITED 또는 혼합: 분당 초과 중 최솟값(가장 빨리 회복되는 provider)
     */
    private Integer estimateWaitSeconds(List<ProviderStatus> providers) {
        if (providers.isEmpty()) {
            return null;
        }

        boolean allDaily = providers.stream()
            .allMatch(ps -> "daily_exhausted".equals(ps.status()));

        if (allDaily) {
            // UTC 자정까지 남은 초
            return secondsUntilUtcMidnight();
        }

        // 분당 초과 provider 중 가장 짧은 대기 시간 반환
        return providers.stream()
            .filter(ps -> "minute_rate_limited".equals(ps.status()))
            .mapToInt(ps -> ps.minuteUsage().resetInSeconds())
            .min()
            .orElse(null);
    }

    /**
     * 비가용 상태일 때 사용자에게 표시할 메시지 생성
     * available=true이면 null 반환 (클라이언트에 불필요한 정보 노출 방지)
     */
    private String buildMessage(List<ProviderStatus> providers, Integer waitSeconds) {
        boolean allDaily = providers.stream()
            .allMatch(ps -> "daily_exhausted".equals(ps.status()));

        if (allDaily) {
            return "오늘의 AI 서비스 사용량이 모두 소진되었습니다. 내일 오전 9시 이후 다시 시도해주세요.";
        }

        if (waitSeconds != null) {
            return String.format("AI 서비스가 잠시 과부하 상태입니다. 약 %d초 후 다시 시도해주세요.", waitSeconds);
        }

        return null;
    }

    /**
     * 오늘 UTC 자정까지 남은 초 계산
     * 내일 UTC 00:00:00에서 현재 시각을 뺀 값
     */
    private int secondsUntilUtcMidnight() {
        Instant now = Instant.now();
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        Instant midnight = tomorrow.atStartOfDay(ZoneOffset.UTC).toInstant();
        long seconds = Duration.between(now, midnight).getSeconds();
        return (int) Math.max(1L, seconds);
    }

    /**
     * 오늘의 UTC 자정 시각을 ISO-8601 문자열로 반환
     * 예: "2026-04-02T00:00:00Z"
     */
    private String todayUtcMidnightIso() {
        LocalDate tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        return tomorrow.atStartOfDay(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
    }
}
