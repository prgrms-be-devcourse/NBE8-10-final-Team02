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
     * AI 작업 전용 가상 스레드 Executor.
     *
     * <p>가상 스레드는 요청마다 즉시 생성되어 202 응답을 블로킹하지 않는다.
     * 동시 AI 호출 수는 AiConcurrencyLimiter(Semaphore)가 제어하므로
     * 스레드 풀 크기를 별도로 튜닝할 필요가 없다.</p>
     */
    @Bean("aiTaskExecutor")
    public Executor aiTaskExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
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

}
