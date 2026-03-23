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

class SelfIntroGenerateValidatorTest {

    private SelfIntroGenerateValidator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(objectMapper);
        validator = new SelfIntroGenerateValidator(jsonSchemaValidator);
    }

    @Test
    @DisplayName("templateId가 ai.self_intro.generate.v1이다")
    void getTemplateId() {
        assertThat(validator.getTemplateId()).isEqualTo("ai.self_intro.generate.v1");
    }

    @Nested
    @DisplayName("schema 검증")
    class SchemaValidation {

        @Test
        @DisplayName("정상 응답은 검증을 통과한다")
        void valid_response() {
            JsonNode node = buildResponse(1, "안녕하세요, 저는", List.of("project_1"));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.warnings()).isEmpty();
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
        @DisplayName("스키마에 없는 임의 필드가 있으면 실패한다")
        void additional_properties_rejected() {
            ObjectNode node = (ObjectNode) buildResponse(1, "안녕하세요, 저는", List.of("project_1"));
            node.put("extraField", "unexpected");

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("questionOrder가 중복되면 실패한다")
        void duplicate_questionOrder() {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode answers = root.putArray("answers");
            answers.add(buildAnswer(1, "안녕하세요, 저는", List.of("project_1")));
            answers.add(buildAnswer(1, "저는 이 회사에", List.of("project_2")));
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder 중복"));
        }

        @Test
        @DisplayName("questionOrder가 누락된 항목이 있으면 실패한다")
        void missing_questionOrder() {
            ObjectNode root = objectMapper.createObjectNode();
            root.putArray("answers").add(buildAnswerWithoutOrder("안녕하세요", List.of("project_1")));
            root.putArray("qualityFlags");

            ValidationResult result = validator.validate(root);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder"));
        }

        @Test
        @DisplayName("answerText가 공백 문자열이면 questionOrder를 포함한 에러와 함께 실패한다")
        void blank_answerText() {
            JsonNode node = buildResponse(1, "   ", List.of("project_1"));

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e ->
                e.contains("answerText가 비어있습니다") && e.contains("questionOrder=1")
            );
        }

        @Test
        @DisplayName("usedEvidenceKeys가 비어있으면 경고와 함께 통과한다")
        void empty_usedEvidenceKeys_warning() {
            JsonNode node = buildResponse(1, "안녕하세요, 저는", List.of());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("usedEvidenceKeys가 비어있습니다"));
        }
    }

    private JsonNode buildResponse(int questionOrder, String answerText, List<String> evidenceKeys) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("answers").add(buildAnswer(questionOrder, answerText, evidenceKeys));
        root.putArray("qualityFlags");
        return root;
    }

    private ObjectNode buildAnswer(int questionOrder, String answerText, List<String> evidenceKeys) {
        ObjectNode answer = buildAnswerWithoutOrder(answerText, evidenceKeys);
        answer.put("questionOrder", questionOrder);
        return answer;
    }

    private ObjectNode buildAnswerWithoutOrder(String answerText, List<String> evidenceKeys) {
        ObjectNode answer = objectMapper.createObjectNode();
        answer.put("questionText", "자기소개를 해주세요");
        answer.put("answerText", answerText);
        ArrayNode keys = answer.putArray("usedEvidenceKeys");
        evidenceKeys.forEach(keys::add);
        answer.putArray("qualityFlags");
        return answer;
    }
}
