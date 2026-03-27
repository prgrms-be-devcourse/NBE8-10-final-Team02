package com.back.backend.domain.ai.validation;

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

class InterviewQuestionsGenerateValidatorTest {

    private InterviewQuestionsGenerateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new InterviewQuestionsGenerateValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.interview.questions.generate.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.interview.questions.generate.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("정상 응답은 검증을 통과한다")
        void valid_response() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "experience", "medium", "자기소개를 해주세요")
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
        @DisplayName("questionType이 허용 enum이 아니면 실패한다")
        void invalid_questionType_enum() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "unknown_type", "medium", "질문입니다")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionType"));
        }

        @Test
        @DisplayName("difficultyLevel이 허용 enum이 아니면 실패한다")
        void invalid_difficultyLevel_enum() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "experience", "very_hard", "질문입니다")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("difficultyLevel"));
        }

        @Test
        @DisplayName("누락된 qualityFlags는 빈 배열로 복구하고 schema_recovered를 남긴다")
        void missing_qualityFlags_recovered() {
            ObjectNode node = objectMapper.createObjectNode();
            ArrayNode questions = node.putArray("questions");
            questions.add(buildQuestion(1, "experience", "medium", "질문입니다"));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(node.get("qualityFlags")).isNotNull();
            assertThat(node.get("qualityFlags").get(0).asText()).isEqualTo("schema_recovered");
        }

        @Test
        @DisplayName("interviewQuestions와 questionId 응답은 schema 기준 키로 복구한다")
        void legacy_keys_recovered() {
            ObjectNode node = objectMapper.createObjectNode();
            ArrayNode questions = node.putArray("interviewQuestions");
            ObjectNode question = questions.addObject();
            question.put("questionId", 1);
            question.put("questionType", "project");
            question.put("difficultyLevel", "medium");
            question.put("questionText", "프로젝트 경험을 설명해주세요.");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(node.has("interviewQuestions")).isFalse();
            assertThat(node.get("questions").get(0).get("questionOrder").asInt()).isEqualTo(1);
            assertThat(node.get("questions").get(0).has("questionId")).isFalse();
            assertThat(node.get("qualityFlags")).isNotNull();
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("questionOrder가 1부터 연속적이지 않으면 실패한다")
        void non_sequential_questionOrder() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "experience", "medium", "질문 1"),
                buildQuestion(3, "technical_cs", "hard", "질문 3")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("누락된 순서: 2"));
        }

        @Test
        @DisplayName("questionOrder가 중복되면 실패한다")
        void duplicate_questionOrder() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "experience", "medium", "질문 1"),
                buildQuestion(1, "technical_cs", "hard", "질문 2")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder 중복"));
        }

        @Test
        @DisplayName("questionText가 공백 문자열이면 questionOrder를 포함한 에러와 함께 실패한다")
        void blank_questionText() {
            JsonNode node = buildResponse(List.of(
                buildQuestion(1, "experience", "medium", "   ")
            ));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                e.contains("questionText가 비어있습니다") && e.contains("questionOrder=1")
            );
        }
    }

    private JsonNode buildResponse(List<ObjectNode> questions) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode questionsArray = root.putArray("questions");
        questions.forEach(questionsArray::add);
        root.putArray("qualityFlags");
        return root;
    }

    private ObjectNode buildQuestion(int questionOrder, String questionType,
                                     String difficultyLevel, String questionText) {
        ObjectNode question = objectMapper.createObjectNode();
        question.put("questionOrder", questionOrder);
        question.put("questionType", questionType);
        question.put("difficultyLevel", difficultyLevel);
        question.put("questionText", questionText);
        return question;
    }
}
