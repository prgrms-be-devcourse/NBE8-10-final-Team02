package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class InterviewFollowupGenerateValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.interview.followup.generate.v1";
    private static final String SCHEMA_FILE = "interview-followup-generate.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public InterviewFollowupGenerateValidator(JsonSchemaValidator jsonSchemaValidator) {
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

        JsonNode followUpQuestion = responseNode.get("followUpQuestion");
        if (followUpQuestion != null && !followUpQuestion.isNull()) {
            validateQuestionText(followUpQuestion, errors);
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }

    /**
     * questionText 공백 문자열 추가 검사
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리
     */
    private void validateQuestionText(JsonNode followUpQuestion, List<String> errors) {
        JsonNode questionText = followUpQuestion.get("questionText");
        if (questionText == null || questionText.asText().isBlank()) {
            errors.add("questionText가 비어있습니다.");
        }
    }
}
