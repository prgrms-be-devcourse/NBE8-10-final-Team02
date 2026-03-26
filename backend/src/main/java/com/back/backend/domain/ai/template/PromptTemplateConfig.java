package com.back.backend.domain.ai.template;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * PromptTemplateRegistry를 빈으로 등록
 * 앱 시작 시 6개 기본 템플릿이 모두 등록된 상태로 생성
 */
@Configuration
public class PromptTemplateConfig {

    @Bean
    public PromptTemplateRegistry promptTemplateRegistry() {
        return PromptTemplateRegistry.createDefault();
    }
}
