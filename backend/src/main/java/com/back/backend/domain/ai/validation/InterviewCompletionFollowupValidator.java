package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * complete 직전 최종 보완 follow-up AI 응답 검증기 (템플릿: ai.interview.followup.complete.v1).
 *
 * 출력 스키마는 runtime follow-up과 동일하지만 templateId와 호출 맥락이 다르다.
 * parentQuestionOrder가 실제 allowed tail order인지 여부는 입력값이 필요하므로 서비스 계층에서 담당한다.
 */
public class InterviewCompletionFollowupValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.interview.followup.complete.v1";
    private static final String SCHEMA_FILE = "interview-followup-complete.schema.json";

    private final JsonSchemaValidator jsonSchemaValidator;

    public InterviewCompletionFollowupValidator(JsonSchemaValidator jsonSchemaValidator) {
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

        JsonNode followUpQuestions = responseNode.path("followUpQuestions");
        Set<Integer> seenParentQuestionOrders = new HashSet<>();
        for (JsonNode followUpQuestion : followUpQuestions) {
            JsonNode questionText = followUpQuestion.get("questionText");
            if (questionText == null || questionText.asText().isBlank()) {
                errors.add("questionText가 비어있습니다.");
            }

            int parentQuestionOrder = followUpQuestion.path("parentQuestionOrder").asInt(-1);
            if (!seenParentQuestionOrders.add(parentQuestionOrder)) {
                errors.add("parentQuestionOrder가 중복되었습니다: " + parentQuestionOrder);
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }
}
