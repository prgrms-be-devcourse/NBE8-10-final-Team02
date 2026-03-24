package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JsonSchemaValidator, ValidationRegistry를 빈으로 등록
 * 앱 시작 시 6개 기본 검증기가 모두 등록된 상태로 생성
 */
@Configuration
public class ValidationConfig {

    /**
     * AI 응답 JSON 파싱 및 schema 검증 담당
     * Spring이 제공하는 ObjectMapper를 주입받아 생성
     */
    @Bean
    public JsonSchemaValidator jsonSchemaValidator(ObjectMapper objectMapper) {
        return new JsonSchemaValidator(objectMapper);
    }

    /**
     * 6개 템플릿별 검증기를 등록한 불변 레지스트리
     * AiPipeline에서 templateId로 적절한 검증기를 조회할 때 사용
     */
    @Bean
    public ValidationRegistry validationRegistry(JsonSchemaValidator jsonSchemaValidator) {
        return ValidationRegistry.createDefault(jsonSchemaValidator);
    }
}
