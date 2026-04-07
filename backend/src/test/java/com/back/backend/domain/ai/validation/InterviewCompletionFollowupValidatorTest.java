package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewCompletionFollowupValidatorTest {

    private InterviewCompletionFollowupValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new InterviewCompletionFollowupValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.interview.followup.complete.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.interview.followup.complete.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("빈 배열 응답은 검증을 통과한다")
        void valid_response_with_empty_array() {
            ObjectNode root = objectMapper.createObjectNode();
            root.putArray("followUpQuestions");
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("여러 thread에 대한 follow-up 응답은 검증을 통과한다")
        void valid_response_with_multiple_followups() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode followUpQuestions = root.putArray("followUpQuestions");
            followUpQuestions.add(buildFollowUpQuestion("첫 번째 보완 질문", 2));
            followUpQuestions.add(buildFollowUpQuestion("두 번째 보완 질문", 5));
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("questionType이 follow_up이 아니면 실패한다")
        void invalid_questionType() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode followUpQuestions = root.putArray("followUpQuestions");
            ObjectNode invalid = buildFollowUpQuestion("후속 질문입니다.", 2);
            invalid.put("questionType", "experience");
            followUpQuestions.add(invalid);
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(error -> error.contains("questionType"));
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("questionText가 공백 문자열이면 실패한다")
        void blank_questionText() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode followUpQuestions = root.putArray("followUpQuestions");
            followUpQuestions.add(buildFollowUpQuestion("   ", 2));
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(error -> error.contains("questionText가 비어있습니다"));
        }

        @Test
        @DisplayName("parentQuestionOrder가 중복되면 실패한다")
        void duplicate_parentQuestionOrder() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode followUpQuestions = root.putArray("followUpQuestions");
            followUpQuestions.add(buildFollowUpQuestion("첫 번째 보완 질문", 2));
            followUpQuestions.add(buildFollowUpQuestion("두 번째 보완 질문", 2));
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(error -> error.contains("parentQuestionOrder가 중복되었습니다"));
        }
    }

    private ObjectNode buildFollowUpQuestion(String questionText, int parentQuestionOrder) {
        ObjectNode followUp = objectMapper.createObjectNode();
        followUp.put("questionType", "follow_up");
        followUp.put("difficultyLevel", "medium");
        followUp.put("questionText", questionText);
        followUp.put("parentQuestionOrder", parentQuestionOrder);
        return followUp;
    }
}
