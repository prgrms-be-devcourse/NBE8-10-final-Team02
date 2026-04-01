package com.back.backend.domain.ai.usage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 사용량 모니터링 설정
 * AiRateLimitProperties를 @ConfigurationProperties 빈으로 활성화
 * AiUsageStore, AiUsageRecorder, AiStatusService는 각자 @Component/@Service로 등록되어 있음
 */
@Configuration
@EnableConfigurationProperties(AiRateLimitProperties.class)
public class AiUsageConfig {
}
