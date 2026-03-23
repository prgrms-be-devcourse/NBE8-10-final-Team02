package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InterviewSummaryValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.interview.summary.v1";
    private static final String SCHEMA_FILE = "interview-summary.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public InterviewSummaryValidator(JsonSchemaValidator jsonSchemaValidator) {
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

        validateShortSummary(responseNode, errors);
        validateStringArray(responseNode.get("strengths"), "strengths", errors);
        validateStringArray(responseNode.get("weaknesses"), "weaknesses", errors);
        validateStringArray(responseNode.get("nextActions"), "nextActions", errors);

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }

    /**
     * shortSummary 공백 문자열 추가 검사.
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리한다.
     */
    private void validateShortSummary(JsonNode responseNode, List<String> errors) {
        JsonNode shortSummary = responseNode.get("shortSummary");
        if (shortSummary == null || shortSummary.asText().isBlank()) {
            errors.add("shortSummary가 비어있습니다.");
        }
    }

    /**
     * 배열 내 공백 항목 및 중복 항목 검사.
     * 공백 항목은 의미 없는 데이터이므로 hard fail, 중복도 동일하게 처리한다.
     */
    private void validateStringArray(JsonNode array, String fieldName, List<String> errors) {
        if (array == null || !array.isArray()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        int index = 0;
        for (JsonNode item : array) {
            String value = item.asText();
            if (value.isBlank()) {
                errors.add(fieldName + "에 공백 항목이 있습니다: index=" + index);
            } else if (!seen.add(value)) {
                errors.add(fieldName + "에 중복 항목이 있습니다: " + value);
            }
            index++;
        }
    }
}
