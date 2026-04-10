package com.back.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 비동기 처리를 위한 설정 클래스.
 *
 * <p>{@code @EnableAsync}를 통해 {@code @Async} 애노테이션을 활성화하고,
 * 문서 텍스트 추출 전용 Executor를 별도 빈으로 등록한다.</p>
 *
 * <p>문서 추출 작업은 시간이 걸릴 수 있으므로 일반 요청 스레드와 분리된
 * {@code documentTaskExecutor}에서 실행한다.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 문서 텍스트 추출 전용 가상 스레드 Executor.
     *
     * <p>추출 파이프라인은 PDFBox(CPU) + Gitleaks subprocess(I/O 대기)가 혼합된 워크로드다.
     * 가상 스레드는 Gitleaks 대기 중 캐리어 스레드를 반납해 다른 문서의 CPU 작업이
     * 그 시간을 활용할 수 있도록 한다. 플랫폼 스레드 풀 크기 튜닝 없이
     * CPU 코어 수가 자연스러운 동시 처리 상한 역할을 한다.</p>
     */
    @Bean("documentTaskExecutor")
    public Executor documentTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * AI 면접 결과 생성 전용 Executor.
     *
     * <p>completeSession()에서 AI 평가 작업을 비동기로 실행할 때 사용.
     * core=2(vCPU 수), max=4(버스트), queue=50(VU 수)으로 설정하여
     * AI 작업이 일반 요청 스레드를 점유하지 않도록 격리한다.
     * CallerRunsPolicy로 큐 포화 시 호출 스레드에서 동기 실행(graceful degradation).</p>
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    @Bean("activityTaskExecutor")
    public Executor activityTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("activity-");
        executor.initialize();
        return executor;
    }

    /**
     * 자소서 AI 답변 생성 전용 Executor.
     *
     * <p>aiTaskExecutor(면접 결과)와 분리하여 자소서 생성 요청이 몰려도
     * 면접 결과 생성 처리에 영향을 주지 않도록 격리한다.
     * core=2, max=4, queue=100 — K6 VU 수 기준.
     * CallerRunsPolicy로 큐 포화 시 호출 스레드에서 동기 실행(graceful degradation).</p>
     */
    @Bean("applicationAiTaskExecutor")
    public Executor applicationAiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("app-ai-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
