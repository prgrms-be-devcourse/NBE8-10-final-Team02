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
     *   <li>corePoolSize=2: 평시 스레드 수 (문서 추출 작업은 많지 않음)</li>
     *   <li>maxPoolSize=4: 동시 업로드 급증 시 최대 스레드 수</li>
     *   <li>queueCapacity=50: 스레드 풀이 가득 찰 경우 대기 큐 크기</li>
     * </ul>
     */
    @Bean("documentTaskExecutor")
    public Executor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("doc-extract-");
        executor.initialize();
        return executor;
    }
}
