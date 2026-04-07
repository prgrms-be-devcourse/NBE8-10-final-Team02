package com.back.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리를 위한 설정 클래스.
 *
 * <p>{@code @EnableAsync}를 통해 {@code @Async} 애노테이션을 활성화하고,
 * 문서 텍스트 추출 전용 Executor를 별도 빈으로 등록한다.</p>
 *
 * <p>문서 추출 작업은 시간이 걸릴 수 있으므로 일반 요청 스레드와 분리된
 * {@code documentTaskExecutor} 풀에서 실행한다.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 문서 텍스트 추출 전용 스레드 풀.
     *
     * <ul>
     *   <li>corePoolSize=4: 평시 스레드 수 — Gitleaks subprocess 대기 중에도 다른 문서 처리 가능</li>
     *   <li>maxPoolSize=8: 동시 업로드 급증 시 최대 스레드 수</li>
     *   <li>queueCapacity=100: 스레드 풀이 가득 찰 경우 대기 큐 크기</li>
     * </ul>
     *
     * <p><b>조정 배경</b>: 2/4/50 → 4/8/100으로 확장.
     * 주요 병목인 Gitleaks subprocess(500ms~2s)가 스레드를 블로킹하므로
     * core를 늘려 다른 추출 작업이 대기 없이 실행되도록 한다.
     * 소형 문서는 SecretMaskingService의 threshold fast-path로 Gitleaks를 건너뛴다.</p>
     */
    @Bean("documentTaskExecutor")
    public Executor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("doc-extract-");
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
}
