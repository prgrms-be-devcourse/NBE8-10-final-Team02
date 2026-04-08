package com.back.backend.domain.ai.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AiClientRouter 빈을 등록
 * ai.provider 값과 등록된 AiClient 구현체 목록을 조합하여 라우터를 생성
 * ai.fallback-provider가 설정되면 기본 provider 실패 시 대체 provider로 자동 전환
 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiClientRouter aiClientRouter(
        List<AiClient> clients,
        @Value("${ai.provider}") String provider,
        @Value("${ai.fallback-provider:}") String fallbackProvider // 빈 문자열이면 fallback 비활성화
    ) {
        AiProvider defaultProv = AiProvider.fromValue(provider);
        AiProvider fallbackProv = fallbackProvider.isBlank() ? null : AiProvider.fromValue(fallbackProvider);
        return new AiClientRouter(clients, defaultProv, fallbackProv);
    }
}
