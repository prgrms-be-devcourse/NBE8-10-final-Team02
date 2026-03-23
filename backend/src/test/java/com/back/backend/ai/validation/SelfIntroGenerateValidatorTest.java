package com.back.backend.ai.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
        void valid_response() throws Exception {
            JsonNode node = objectMapper.readTree(validResponse());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        @DisplayName("필수 필드 누락 시 실패한다")
        void missing_required_field() throws Exception {
            JsonNode node = objectMapper.readTree("""
                {
                  "qualityFlags": []
                }
                """);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).isNotEmpty();
        }

        @Test
        @DisplayName("스키마에 없는 임의 필드가 있으면 실패한다")
        void additional_properties_rejected() throws Exception {
            String json = validResponse().replace(
                "\"qualityFlags\": []",
                "\"qualityFlags\": [], \"extraField\": \"unexpected\""
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
        }
    }

    @Nested
    @DisplayName("cross-field 검증")
    class CrossFieldValidation {

        @Test
        @DisplayName("questionOrder가 중복되면 실패한다")
        void duplicate_questionOrder() throws Exception {
            JsonNode node = objectMapper.readTree(duplicateOrderResponse());

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder 중복"));
        }

        @Test
        @DisplayName("questionOrder가 누락된 항목이 있으면 실패한다")
        void missing_questionOrder() throws Exception {
            JsonNode node = objectMapper.readTree("""
                {
                  "answers": [
                    {
                      "questionText": "자기소개를 해주세요",
                      "answerText": "안녕하세요",
                      "usedEvidenceKeys": ["project_1"],
                      "qualityFlags": []
                    }
                  ],
                  "qualityFlags": []
                }
                """);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder"));
        }

        @Test
        @DisplayName("answerText가 공백 문자열이면 실패한다")
        void blank_answerText() throws Exception {
            String json = validResponse().replace(
                "\"answerText\": \"안녕하세요, 저는\"",
                "\"answerText\": \"   \""
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("answerText가 비어있습니다"));
        }

        @Test
        @DisplayName("answerText 오류 메시지에 questionOrder가 포함된다")
        void blank_answerText_error_contains_order() throws Exception {
            String json = validResponse().replace(
                "\"answerText\": \"안녕하세요, 저는\"",
                "\"answerText\": \"   \""
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.errors()).anyMatch(e -> e.contains("questionOrder=1"));
        }

        @Test
        @DisplayName("usedEvidenceKeys가 비어있으면 경고와 함께 통과한다")
        void empty_usedEvidenceKeys_warning() throws Exception {
            String json = validResponse().replace(
                "\"usedEvidenceKeys\": [\"project_1\"]",
                "\"usedEvidenceKeys\": []"
            );
            JsonNode node = objectMapper.readTree(json);

            ValidationResult result = validator.validate(node);

            assertThat(result.valid()).isTrue();
            assertThat(result.warnings()).anyMatch(w -> w.contains("usedEvidenceKeys가 비어있습니다"));
        }
    }

    private String validResponse() {
        return """
            {
              "answers": [
                {
                  "questionOrder": 1,
                  "questionText": "자기소개를 해주세요",
                  "answerText": "안녕하세요, 저는",
                  "usedEvidenceKeys": ["project_1"],
                  "qualityFlags": []
                }
              ],
              "qualityFlags": []
            }
            """;
    }

    private String duplicateOrderResponse() {
        return """
            {
              "answers": [
                {
                  "questionOrder": 1,
                  "questionText": "자기소개를 해주세요",
                  "answerText": "안녕하세요, 저는",
                  "usedEvidenceKeys": ["project_1"],
                  "qualityFlags": []
                },
                {
                  "questionOrder": 1,
                  "questionText": "지원 동기를 말씀해 주세요",
                  "answerText": "저는 이 회사에",
                  "usedEvidenceKeys": ["project_2"],
                  "qualityFlags": []
                }
              ],
              "qualityFlags": []
            }
            """;
    }
}
