package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

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

        JsonNode followUpQuestion = responseNode.get("followUpQuestion");
        if (followUpQuestion != null && !followUpQuestion.isNull()) {
            JsonNode questionText = followUpQuestion.get("questionText");
            if (questionText == null || questionText.asText().isBlank()) {
                errors.add("questionText가 비어있습니다.");
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }
}
