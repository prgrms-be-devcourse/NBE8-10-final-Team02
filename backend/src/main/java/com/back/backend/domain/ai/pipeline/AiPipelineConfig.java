package com.back.backend.domain.ai.pipeline;

import com.back.backend.domain.ai.client.AiClientRouter;
import com.back.backend.domain.ai.template.PromptLoader;
import com.back.backend.domain.ai.template.PromptTemplateRegistry;
import com.back.backend.domain.ai.validation.JsonSchemaValidator;
import com.back.backend.domain.ai.validation.ValidationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiPipelineConfig {

    @Bean
    public PromptLoader promptLoader() {
        return new PromptLoader();
    }

    @Bean
    public AiPipeline aiPipeline(
            AiClientRouter aiClientRouter,
            PromptTemplateRegistry promptTemplateRegistry,
            ValidationRegistry validationRegistry,
            PromptLoader promptLoader,
            JsonSchemaValidator jsonSchemaValidator
    ) {
        return new AiPipeline(
                aiClientRouter,
                promptTemplateRegistry,
                validationRegistry,
                promptLoader,
                jsonSchemaValidator
        );
    }
}
