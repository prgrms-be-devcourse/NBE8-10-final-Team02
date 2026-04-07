package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 인성 문제은행 답변 평가 AI 응답 검증기 (템플릿: ai.practice.evaluate.behavioral.v1)
 */
public class PracticeEvaluateBehavioralValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.practice.evaluate.behavioral.v1";
    private static final String SCHEMA_FILE = "practice-evaluate.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public PracticeEvaluateBehavioralValidator(JsonSchemaValidator jsonSchemaValidator) {
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public ValidationResult validate(JsonNode responseNode) {
        ValidationResult schemaResult = jsonSchemaValidator.validateSchema(responseNode, SCHEMA_FILE);
        if (!schemaResult.valid()) {
            return schemaResult;
        }

        List<String> errors = new ArrayList<>();
        validateNotBlank(responseNode, "feedback", errors);
        validateNotBlank(responseNode, "modelAnswer", errors);

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }

    private void validateNotBlank(JsonNode node, String field, List<String> errors) {
        JsonNode value = node.get(field);
        if (value == null || value.asText().isBlank()) {
            errors.add(field + "가 비어있습니다.");
        }
    }
}
