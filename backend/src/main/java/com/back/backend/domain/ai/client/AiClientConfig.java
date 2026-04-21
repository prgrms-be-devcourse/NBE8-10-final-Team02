package com.back.backend.domain.ai.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiClientRouter 빈을 등록
 * ai.provider 값과 등록된 AiClient 구현체 목록을 조합하여 라우터를 생성
 * fallback chain은 각 PromptTemplate.RetryPolicy.fallbackChain에 per-template으로 정의됨
 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiClientRouter aiClientRouter(
        List<AiClient> clients,
        @Value("${ai.provider}") String provider
    ) {
        return new AiClientRouter(clients, AiProvider.fromValue(provider));
    }
}
