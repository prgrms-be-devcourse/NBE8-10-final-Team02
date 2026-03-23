package com.back.backend.domain.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewFollowupGenerateValidatorTest {

    private InterviewFollowupGenerateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new InterviewFollowupGenerateValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.interview.followup.generate.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.interview.followup.generate.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("followUpQuestion이 있는 정상 응답은 검증을 통과한다")
        void valid_response_with_followup() {
            JsonNode node = buildResponse(buildFollowUpQuestion("후속 질문입니다.", 1));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("followUpQuestion이 null인 응답은 검증을 통과한다")
        void valid_response_with_null_followup() {
            ObjectNode node = objectMapper.createObjectNode();
            node.putNull("followUpQuestion");
            node.putArray("qualityFlags");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("필수 필드 누락 시 실패한다")
        void missing_required_field() {
            ObjectNode node = objectMapper.createObjectNode();
            node.putArray("qualityFlags");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("questionType이 follow_up이 아니면 실패한다")
        void invalid_questionType() {
            ObjectNode followUp = buildFollowUpQuestion("후속 질문입니다.", 1);
            followUp.put("questionType", "experience");
            JsonNode node = buildResponse(followUp);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionType"));
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("questionText가 공백 문자열이면 실패한다")
        void blank_questionText() {
            JsonNode node = buildResponse(buildFollowUpQuestion("   ", 1));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionText가 비어있습니다"));
        }
    }

    private JsonNode buildResponse(ObjectNode followUpQuestion) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("followUpQuestion", followUpQuestion);
        root.putArray("qualityFlags");
        return root;
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
