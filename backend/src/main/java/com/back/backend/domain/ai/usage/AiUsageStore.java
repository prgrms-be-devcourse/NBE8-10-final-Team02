package com.back.backend.domain.ai.usage;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * AI API 호출의 인메모리 슬라이딩 윈도우 통계
 * 분당 요청 수(RPM)와 분당 토큰 수(TPM)를 실시간 추적
 * 서버 재시작 시 데이터 초기화됨 (일간 통계는 DB에 영속 보관)
 */
@Component
public class AiUsageStore {

    /** provider 코드 → 슬라이딩 윈도우 */
    private final ConcurrentHashMap<String, SlidingWindow> windows = new ConcurrentHashMap<>();

    /**
     * AI 호출 성공 시 요청 타임스탬프와 토큰 수 기록
     *
     * @param provider AI provider 코드 (예: "gemini")
     * @param tokens   해당 호출의 총 토큰 수
     */
    public void recordRequest(String provider, long tokens) {
        getWindow(provider).record(Instant.now(), tokens);
    }

    /**
     * 현재 시점 기준 최근 60초 내 요청 수(RPM) 반환
     * 오래된 항목은 제거(evict) 후 계산
     *
     * @param provider AI provider 코드
     * @return 현재 분당 요청 수
     */
    public int currentRpm(String provider) {
        return getWindow(provider).currentRpm();
    }

    /**
     * 현재 시점 기준 최근 60초 내 토큰 수(TPM) 반환
     * 오래된 항목은 제거(evict) 후 계산
     *
     * @param provider AI provider 코드
     * @return 현재 분당 토큰 수
     */
    public long currentTpm(String provider) {
        return getWindow(provider).currentTpm();
    }

    /**
     * 가장 오래된 요청 이후 60초가 지나기까지 남은 초 반환
     * 요청 기록이 없으면 0 반환
     * 최소 1초, 최대 60초로 클램핑
     *
     * @param provider AI provider 코드
     * @return 윈도우 초기화까지 남은 초
     */
    public int secondsUntilReset(String provider) {
        return getWindow(provider).secondsUntilReset();
    }

    private SlidingWindow getWindow(String provider) {
        return windows.computeIfAbsent(provider, k -> new SlidingWindow());
    }

    /**
     * 60초 슬라이딩 윈도우 — 요청 타임스탬프와 토큰 수를 관리
     * 스레드 안전: ConcurrentLinkedDeque 사용
     */
    static class SlidingWindow {

        private static final long WINDOW_MS = 60_000L; // 60초

        /** 요청 시각 큐 (오래된 순서대로 앞에 위치) */
        private final Deque<Instant> requestTimestamps = new ConcurrentLinkedDeque<>();
        /** 토큰 사용 기록 큐 — long[]{epochMilli, tokens} */
        private final Deque<long[]> tokenEntries = new ConcurrentLinkedDeque<>();

        /** 현재 시각으로 요청 기록 추가 */
        void record(Instant now, long tokens) {
            requestTimestamps.addLast(now);
            tokenEntries.addLast(new long[]{now.toEpochMilli(), tokens});
        }

        /** 최근 60초 내 요청 수 반환 */
        int currentRpm() {
            evictOldRequests(Instant.now());
            return requestTimestamps.size();
        }

        /** 최근 60초 내 토큰 합산 반환 */
        long currentTpm() {
            Instant now = Instant.now();
            evictOldTokens(now);
            return tokenEntries.stream()
                .mapToLong(entry -> entry[1])
                .sum();
        }

        /** 윈도우 초기화까지 남은 초 */
        int secondsUntilReset() {
            Instant oldest = requestTimestamps.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long resetAt = oldest.toEpochMilli() + WINDOW_MS;
            long remaining = (resetAt - Instant.now().toEpochMilli()) / 1000L;
            // 최소 1초, 최대 60초로 클램핑
            return (int) Math.max(1L, Math.min(60L, remaining));
        }

        /** 60초가 지난 요청 타임스탬프 제거 */
        private void evictOldRequests(Instant now) {
            long cutoff = now.toEpochMilli() - WINDOW_MS;
            while (!requestTimestamps.isEmpty()) {
                Instant front = requestTimestamps.peekFirst();
                if (front != null && front.toEpochMilli() <= cutoff) {
                    requestTimestamps.pollFirst();
                } else {
                    break;
                }
            }
        }

        /** 60초가 지난 토큰 항목 제거 */
        private void evictOldTokens(Instant now) {
            long cutoff = now.toEpochMilli() - WINDOW_MS;
            while (!tokenEntries.isEmpty()) {
                long[] front = tokenEntries.peekFirst();
                if (front != null && front[0] <= cutoff) {
                    tokenEntries.pollFirst();
                } else {
                    break;
                }
            }
        }
    }
}
