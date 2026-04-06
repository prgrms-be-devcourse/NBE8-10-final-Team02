package com.back.backend.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 분석 파이프라인용 비동기 실행 설정.
 *
 * 분석 작업(clone → 정적 분석 → AI 요약)은 수 분이 소요될 수 있어
 * 전용 스레드 풀에서 실행한다. 웹 요청 스레드를 블록하지 않는다.
 *
 * 빈 이름 "analysisExecutor"를 @Async("analysisExecutor")에서 참조한다.
 */
@EnableAsync
@Configuration
public class AsyncAnalysisConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Bean(name = "analysisExecutor")
    public Executor analysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("analysis-");
        executor.initialize();
        return executor;
    }

    /**
     * 배치 분석 시 repo별 ①②③(significance check, clone, static analysis)을 병렬로 실행하는 전용 풀.
     *
     * analysisExecutor와 분리하여 데드락을 방지한다.
     * (executeBatchAsync가 analysisExecutor 스레드를 점유한 채 같은 풀에서 태스크를 기다리면 데드락 발생)
     *
     * corePoolSize=2: OCI 4 CPU 환경에서 정적 분석기가 CPU 2개 기준으로 설계됨.
     * 2 repo 동시 분석 × CPU 2개 = 4 CPU → 서버 전체 CPU를 초과하지 않는 상한.
     */
    @Bean(name = "parallelAnalysisExecutor")
    public Executor parallelAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("parallel-analysis-");
        executor.initialize();
        return executor;
    }
}
