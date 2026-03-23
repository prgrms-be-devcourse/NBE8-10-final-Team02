package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SelfIntroGenerateValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.self_intro.generate.v1";
    private static final String SCHEMA_FILE = "self-intro-generate.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public SelfIntroGenerateValidator(JsonSchemaValidator jsonSchemaValidator) {
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
        List<String> warnings = new ArrayList<>();

        JsonNode answers = responseNode.get("answers");
        if (answers != null && answers.isArray()) {
            validateQuestionOrderUniqueness(answers, errors);
            validateAnswerTextLength(answers, errors);
            validateUsedEvidenceKeys(answers, warnings);
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        if (!warnings.isEmpty()) {
            return ValidationResult.successWithWarnings(warnings);
        }
        return ValidationResult.success();
    }

    /**
     * questionOrder 중복 금지 — hard fail
     */
    private void validateQuestionOrderUniqueness(JsonNode answers, List<String> errors) {
        Set<Integer> orders = new HashSet<>();
        for (JsonNode answer : answers) {
            JsonNode orderNode = answer.get("questionOrder");
            if (orderNode == null || orderNode.isNull()) {
                errors.add("questionOrder가 누락된 항목이 있습니다.");
                continue;
            }
            int order = orderNode.asInt();
            if (!orders.add(order)) {
                errors.add("questionOrder 중복: " + order);
            }
        }
    }

    /**
     * answerText 공백 문자열 추가 검사
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백(" ", "\t" 등)으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리한다.
     */
    private void validateAnswerTextLength(JsonNode answers, List<String> errors) {
        for (JsonNode answer : answers) {
            JsonNode answerText = answer.get("answerText");
            int order = answer.path("questionOrder").asInt();
            if (answerText == null || answerText.asText().isBlank()) {
                errors.add("answerText가 비어있습니다: questionOrder=" + order);
            }
        }
    }

    /**
     * usedEvidenceKeys 비어있으면 경고 (hard fail 아님)
     * 근거 없는 답변이지만 자소서 자체는 유효할 수 있으므로 warning 처리
     */
    private void validateUsedEvidenceKeys(JsonNode answers, List<String> warnings) {
        for (JsonNode answer : answers) {
            JsonNode keys = answer.get("usedEvidenceKeys");
            int order = answer.path("questionOrder").asInt();
            if (keys == null || keys.isEmpty()) {
                warnings.add("usedEvidenceKeys가 비어있습니다: questionOrder=" + order);
            }
        }
    }
}
