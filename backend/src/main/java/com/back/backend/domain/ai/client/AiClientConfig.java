package com.back.backend.domain.ai.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiClientRouter 빈을 등록
 * ai.provider 값과 등록된 AiClient 구현체 목록을 조합하여 라우터를 생성
 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiClientRouter aiClientRouter(
        List<AiClient> clients,
        @Value("${ai.provider}") String provider
    ) {
        AiProvider defaultProvider = AiProvider.valueOf(provider.toUpperCase());
        return new AiClientRouter(clients, defaultProvider);
    }
}
