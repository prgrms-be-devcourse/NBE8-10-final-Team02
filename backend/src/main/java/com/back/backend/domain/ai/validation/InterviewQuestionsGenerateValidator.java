package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 면접 질문 생성 AI 응답 검증기 (템플릿: ai.interview.questions.generate.v1).
 *
 * JSON Schema 검증 이후 스키마만으로 잡을 수 없는 도메인 규칙을 추가로 검사
 * questionOrder 중복 및 1부터 N까지 연속성 — hard fail
 * questionText 공백 문자열 — hard fail
 *
 * questionType, difficultyLevel의 enum 유효성은 JSON Schema가 담당
 */
public class InterviewQuestionsGenerateValidator implements AiResponseValidator {

    private static final String TEMPLATE_ID = "ai.interview.questions.generate.v1";
    private static final String SCHEMA_FILE = "interview-questions-generate.schema.json";
    private static final String SCHEMA_RECOVERED = "schema_recovered";

    private final JsonSchemaValidator jsonSchemaValidator;

    public InterviewQuestionsGenerateValidator(JsonSchemaValidator jsonSchemaValidator) {
        this.jsonSchemaValidator = jsonSchemaValidator;
    }

    @Override
    public String getTemplateId() {
        return TEMPLATE_ID;
    }

    @Override
    public ValidationResult validate(JsonNode responseNode) {
        recoverCommonSchemaMistakes(responseNode);

        ValidationResult schemaResult = jsonSchemaValidator.validateSchema(responseNode, SCHEMA_FILE);
        if (!schemaResult.valid()) {
            return schemaResult;
        }

        List<String> errors = new ArrayList<>();

        JsonNode questions = responseNode.get("questions");
        if (questions != null && questions.isArray()) {
            validateQuestionOrderSequential(questions, errors);
            validateQuestionTextLength(questions, errors);
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success();
    }

    /**
     * 실제 AI 응답에서 반복적으로 관찰된 가벼운 스키마 이탈을 제한적으로 복구한다.
     * - interviewQuestions -> questions
     * - questionId -> questionOrder
     * - missing qualityFlags -> []
     *
     * 복구 후에도 JSON Schema를 다시 통과해야만 유효 응답으로 인정한다.
     */
    private void recoverCommonSchemaMistakes(JsonNode responseNode) {
        if (!(responseNode instanceof ObjectNode root)) {
            return;
        }

        boolean recovered = false;

        if (root.has("interviewQuestions")) {
            JsonNode legacyQuestions = root.remove("interviewQuestions");
            if (!root.has("questions") && legacyQuestions != null) {
                root.set("questions", legacyQuestions);
            }
            recovered = true;
        }

        if (!root.has("qualityFlags")) {
            root.putArray("qualityFlags");
            recovered = true;
        }

        JsonNode questionsNode = root.get("questions");
        if (questionsNode instanceof ArrayNode questions) {
            for (JsonNode questionNode : questions) {
                if (!(questionNode instanceof ObjectNode questionObject)) {
                    continue;
                }

                JsonNode legacyQuestionId = questionObject.get("questionId");
                if (legacyQuestionId != null) {
                    if (!questionObject.has("questionOrder") && legacyQuestionId.canConvertToInt()) {
                        questionObject.set("questionOrder", legacyQuestionId.deepCopy());
                    }
                    questionObject.remove("questionId");
                    recovered = true;
                }
            }
        }

        if (recovered) {
            ArrayNode qualityFlags = (ArrayNode) root.withArray("qualityFlags");
            boolean alreadyMarked = false;
            for (JsonNode qualityFlag : qualityFlags) {
                if (SCHEMA_RECOVERED.equals(qualityFlag.asText())) {
                    alreadyMarked = true;
                    break;
                }
            }
            if (!alreadyMarked) {
                qualityFlags.add(SCHEMA_RECOVERED);
            }
        }
    }

    /**
     * questionOrder는 1부터 시작해 questions 개수만큼 중복 없이 연속적이어야 한다 — hard fail.
     * 스키마의 minimum:1은 개별 값만 체크하므로 연속성과 중복은 별도 검증이 필요하다.
     * 기대값: {1, 2, ..., n} (n = questions 배열 길이)
     */
    private void validateQuestionOrderSequential(JsonNode questions, List<String> errors) {
        int totalCount = questions.size();
        Set<Integer> orderSet = new HashSet<>();

        for (JsonNode question : questions) {
            JsonNode orderNode = question.get("questionOrder");
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
     * questionText 공백 문자열 추가 검사
     * JSON Schema의 minLength:1은 빈 문자열("")만 차단하므로
     * 공백으로 채워진 응답은 통과된다. 도메인 규칙상 이를 실패로 처리
     */
    private void validateQuestionTextLength(JsonNode questions, List<String> errors) {
        int index = 0;
        for (JsonNode question : questions) {
            int order = question.path("questionOrder").asInt(-1);
            String label = order == -1 ? "index=" + index : "questionOrder=" + order;
            JsonNode questionText = question.get("questionText");
            if (questionText == null || questionText.asText().isBlank()) {
                errors.add("questionText가 비어있습니다: " + label);
            }
            index++;
        }
    }
}
