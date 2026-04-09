package com.back.backend.domain.ai.pipeline;

import com.back.backend.global.exception.ErrorCode;
import com.back.backend.global.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 시스템 전체 AI API 동시 호출 수를 제한하는 Bulkhead
 * fair Semaphore로 FIFO 순서를 보장하여 2 vCPU 환경에서
 * CPU thrashing과 cascade failure를 방지함
 */
@Component
public class AiConcurrencyLimiter {

    private static final Logger log = LoggerFactory.getLogger(AiConcurrencyLimiter.class);

    private final Semaphore semaphore;
    private final Duration queueTimeout;

    public AiConcurrencyLimiter(
        @Value("${ai.concurrency.max-concurrent-calls:4}") int maxConcurrentCalls,
        @Value("${ai.concurrency.queue-timeout-seconds:90}") long queueTimeoutSeconds
    ) {
        this.semaphore = new Semaphore(maxConcurrentCalls, true);
        this.queueTimeout = Duration.ofSeconds(queueTimeoutSeconds);
        log.info("[AiConcurrencyLimiter] 초기화 완료: maxConcurrentCalls={}, queueTimeout={}s",
            maxConcurrentCalls, queueTimeoutSeconds);
    }

    /**
     * 동시성 제한 하에서 AI 작업을 실행
     * permit을 획득할 때까지 최대 queueTimeout만큼 대기하며,
     * 초과 시 retryable한 503 ServiceException을 던짐
     *
     * @param action 실행할 AI 작업
     * @return AI 작업의 결과
     */
    public <T> T executeWithLimit(Supplier<T> action) {
        boolean acquired;
        try {
            acquired = semaphore.tryAcquire(queueTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.",
                true
            );
        }

        if (!acquired) {
            log.warn("[AiConcurrencyLimiter] 대기 시간 초과 ({}s), 요청 거부", queueTimeout.getSeconds());
            throw new ServiceException(
                ErrorCode.EXTERNAL_SERVICE_TEMPORARILY_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE,
                "AI 서비스가 혼잡합니다. 잠시 후 다시 시도해주세요.",
                true
            );
        }

        try {
            return action.get();
        } finally {
            semaphore.release();
        }
    }

    /** 현재 사용 가능한 permit 수 (모니터링용) */
    public int availablePermits() {
        return semaphore.availablePermits();
    }

    /** 현재 대기 중인 스레드 수 (모니터링용) */
    public int queueLength() {
        return semaphore.getQueueLength();
    }
}
