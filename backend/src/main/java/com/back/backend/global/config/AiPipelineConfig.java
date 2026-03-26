package com.back.backend.global.config;

import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.pipeline.AiPipeline;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PromptLoader, AiPipeline을 빈으로 등록
 */
@Configuration
public class AiPipelineConfig {

    @Bean
    public PromptLoader promptLoader() {
        return new PromptLoader();
    }

    @Bean
    public AiPipeline aiPipeline(
            AiClientRouter router,
            PromptTemplateRegistry templateRegistry,
            ValidationRegistry validationRegistry,
            PromptLoader promptLoader,
            JsonSchemaValidator jsonSchemaValidator
    ) {
        return new AiPipeline(router, templateRegistry, validationRegistry, promptLoader, jsonSchemaValidator);
    }
}
