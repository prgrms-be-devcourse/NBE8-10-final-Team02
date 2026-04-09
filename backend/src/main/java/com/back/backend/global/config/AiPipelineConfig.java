package com.back.backend.global.config;

import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.pipeline.AiConcurrencyLimiter;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.recovery.TruncatedJsonArrayRecovery;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.usage.AiUsageRecorder;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PromptLoader, AiPipeline을 빈으로 등록
 * AiUsageRecorder를 AiPipeline에 주입하여 성공/rate limit 사용량 기록 활성화
 * AiConcurrencyLimiter를 AiPipeline에 주입하여 시스템 전체 AI 동시 호출 수 제한
 */
@Configuration
public class AiPipelineConfig {

    @Bean
    public PromptLoader promptLoader() {
        return new PromptLoader();
    }

    @Bean
    public TruncatedJsonArrayRecovery truncatedJsonArrayRecovery(ObjectMapper objectMapper) {
        return new TruncatedJsonArrayRecovery(objectMapper);
    }

    @Bean
    public AiPipeline aiPipeline(
            AiClientRouter router,
            PromptTemplateRegistry templateRegistry,
            ValidationRegistry validationRegistry,
            PromptLoader promptLoader,
            JsonSchemaValidator jsonSchemaValidator,
            AiUsageRecorder usageRecorder,
            AiConcurrencyLimiter concurrencyLimiter,
            TruncatedJsonArrayRecovery truncatedJsonArrayRecovery
    ) {
        return new AiPipeline(router, templateRegistry, validationRegistry, promptLoader,
                jsonSchemaValidator, usageRecorder, concurrencyLimiter, truncatedJsonArrayRecovery);
    }
}
