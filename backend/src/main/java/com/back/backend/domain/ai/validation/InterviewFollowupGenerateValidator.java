package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * 꼬리 질문 생성 AI 응답 검증기 (템플릿: ai.interview.followup.generate.v1).
 *
 * JSON Schema 검증 이후 스키마만으로 잡을 수 없는 도메인 규칙을 추가로 검사
 * followUpQuestion이 null이면 꼬리 질문 없음(스킵)으로 정상 처리
 * followUpQuestion이 존재하면 questionText 공백 문자열 — hard fail
 *
 * parentQuestionOrder 일치 검증은 입력값이 필요하므로 파이프라인 계층에서 담당
 */
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
        // null은 꼬리 질문 없음(스킵)을 의미하므로 정상 케이스
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
