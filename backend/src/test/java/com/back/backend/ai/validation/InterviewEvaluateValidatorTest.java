package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewEvaluateValidatorTest {

    private InterviewEvaluateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new InterviewEvaluateValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.interview.evaluate.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.interview.evaluate.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("정상 응답은 검증을 통과한다")
        void valid_response() {
            JsonNode node = buildResponse(80, "전반적으로 좋습니다.", List.of(
                buildAnswer(1, 75, "근거가 충분합니다.")
            ));

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
        @DisplayName("totalScore가 100을 초과하면 실패한다")
        void totalScore_exceeds_max() {
            JsonNode node = buildResponse(101, "피드백입니다.", List.of(
                buildAnswer(1, 75, "근거입니다.")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("totalScore"));
        }

        @Test
        @DisplayName("score가 100을 초과하면 실패한다")
        void score_exceeds_max() {
            JsonNode node = buildResponse(80, "피드백입니다.", List.of(
                buildAnswer(1, 101, "근거입니다.")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("score"));
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("summaryFeedback가 공백 문자열이면 실패한다")
        void blank_summaryFeedback() {
            JsonNode node = buildResponse(80, "   ", List.of(
                buildAnswer(1, 75, "근거입니다.")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("summaryFeedback가 비어있습니다"));
        }

        @Test
        @DisplayName("questionOrder가 중복되면 실패한다")
        void duplicate_questionOrder() {
            JsonNode node = buildResponse(80, "피드백입니다.", List.of(
                buildAnswer(1, 75, "근거 1"),
                buildAnswer(1, 60, "근거 2")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder 중복"));
        }

        @Test
        @DisplayName("questionOrder가 1부터 연속적이지 않으면 실패한다")
        void non_sequential_questionOrder() {
            JsonNode node = buildResponse(80, "피드백입니다.", List.of(
                buildAnswer(1, 75, "근거 1"),
                buildAnswer(3, 60, "근거 2")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("누락된 순서: 2"));
        }

        @Test
        @DisplayName("evaluationRationale가 공백 문자열이면 questionOrder를 포함한 에러와 함께 실패한다")
        void blank_evaluationRationale() {
            JsonNode node = buildResponse(80, "피드백입니다.", List.of(
                buildAnswer(1, 75, "   ")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                e.contains("evaluationRationale가 비어있습니다") && e.contains("questionOrder=1")
            );
        }
    }

    private JsonNode buildResponse(int totalScore, String summaryFeedback, List<ObjectNode> answers) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("totalScore", totalScore);
        root.put("summaryFeedback", summaryFeedback);
        ArrayNode answersArray = root.putArray("answers");
        answers.forEach(answersArray::add);
        root.putArray("qualityFlags");
        return root;
    }

    private ObjectNode buildAnswer(int questionOrder, int score, String evaluationRationale) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("questionOrder", questionOrder);
        answer.put("score", score);
        answer.put("evaluationRationale", evaluationRationale);
        answer.putArray("tagNames");
        return answer;
    }
}
