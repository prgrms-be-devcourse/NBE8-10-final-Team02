package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InterviewEvaluateValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.interview.evaluate.v1";
    private static final String SCHEMA_FILE = "interview-evaluate.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public InterviewEvaluateValidator(JsonSchemaValidator jsonSchemaValidator) {
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

        validateSummaryFeedback(responseNode, errors);

        JsonNode answers = responseNode.get("answers");
        if (answers != null && answers.isArray()) {
            validateQuestionOrderSequential(answers, errors);
            validateEvaluationRationale(answers, errors);
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }

    /**
     * summaryFeedback 공백 문자열 추가 검사.
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리한다.
     */
    private void validateSummaryFeedback(JsonNode responseNode, List<String> errors) {
        JsonNode summaryFeedback = responseNode.get("summaryFeedback");
        if (summaryFeedback == null || summaryFeedback.asText().isBlank()) {
            errors.add("summaryFeedback가 비어있습니다.");
        }
    }

    /**
     * questionOrder는 1부터 시작해 answers 개수만큼 중복 없이 연속적이어야 한다 — hard fail.
     * 스키마의 minimum:1은 개별 값만 체크하므로 연속성과 중복은 별도 검증이 필요하다.
     * 기대값: {1, 2, ..., n} (n = answers 배열 길이)
     */
    private void validateQuestionOrderSequential(JsonNode answers, List<String> errors) {
        int totalCount = answers.size();
        Set<Integer> orderSet = new HashSet<>();

        for (JsonNode answer : answers) {
            JsonNode orderNode = answer.get("questionOrder");
            if (orderNode == null || orderNode.isNull()) {
                continue; // 스키마 검증에서 이미 잡힘
            }
            int order = orderNode.asInt();
            if (!orderSet.add(order)) {
                errors.add("questionOrder 중복: " + order);
            }
        }

        for (int i = 1; i <= totalCount; i++) {
            if (!orderSet.contains(i)) {
                errors.add("questionOrder가 1부터 연속적이지 않습니다. 누락된 순서: " + i);
            }
        }
    }

    /**
     * evaluationRationale 공백 문자열 추가 검사.
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리한다.
     */
    private void validateEvaluationRationale(JsonNode answers, List<String> errors) {
        int index = 0;
        for (JsonNode answer : answers) {
            int order = answer.path("questionOrder").asInt(-1);
            String label = order == -1 ? "index=" + index : "questionOrder=" + order;
            JsonNode rationale = answer.get("evaluationRationale");
            if (rationale == null || rationale.asText().isBlank()) {
                errors.add("evaluationRationale가 비어있습니다: " + label);
            }
            index++;
        }
    }
}
