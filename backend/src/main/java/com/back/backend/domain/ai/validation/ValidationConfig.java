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

    @Bean
    public JsonSchemaValidator jsonSchemaValidator(ObjectMapper objectMapper) {
        return new JsonSchemaValidator(objectMapper);
    }

    @Bean
    public ValidationRegistry validationRegistry(JsonSchemaValidator jsonSchemaValidator) {
        return ValidationRegistry.createDefault(jsonSchemaValidator);
    }
}
